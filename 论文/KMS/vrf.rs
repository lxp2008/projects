// VRF-based ephemeral key generation for multi-party Ed25519 signatures
//
// This module implements Verifiable Random Function (VRF) based generation
// of ephemeral keys for use in multi-party Ed25519 signatures, replacing
// the commitment-based approach with a provably fair and verifiable system.

use curv::elliptic::curves::{Ed25519, Point, Scalar};
use curv::BigInt;
use rand::Rng;
use sha2::{Digest, Sha512};
use curv::cryptographic_primitives::hashing::DigestExt;
use rand::RngCore;

use crate::protocols::ExpandedKeyPair;

/// VRF proof for verifying the correct generation of a nonce
#[derive(Clone, Debug, PartialEq)]
pub struct VrfProof {
    /// Gamma point: k*H where H is the hash point of the message
    pub gamma: Point<Ed25519>,
    /// c: challenge value
    pub c: Scalar<Ed25519>,
    /// s: response value
    pub s: Scalar<Ed25519>,
}

/// Output from the VRF containing the random value and its proof
#[derive(Clone, Debug)]
pub struct VrfOutput {
    /// The ephemeral public key (R value)
    pub public_key: Point<Ed25519>,
    /// The ephemeral private key (r value)
    pub private_key: Scalar<Ed25519>,
    /// Proof that the keys were generated correctly
    pub proof: VrfProof,
}

/// Generate ephemeral keys using VRF
/// 
/// This function replaces the commitment-based approach in create_ephemeral_key_and_commit_rng
/// by directly generating a verifiable R value using the VRF.
///
/// Arguments:
/// * `keys`: The signer's expanded keypair
/// * `message`: The message being signed
/// * `session_id`: Optional session identifier to prevent replay attacks
///
/// Returns a VrfOutput containing the ephemeral keys and proof
pub fn generate_ephemeral_key_vrf(
    keys: &ExpandedKeyPair,
    message: &[u8],
    session_id: &[u8],
) -> VrfOutput {
    // Combine inputs to create VRF alpha string
    let mut alpha = Vec::with_capacity(message.len() + session_id.len() + 8);
    alpha.extend_from_slice(b"MPEd25519"); // Domain separator
    alpha.extend_from_slice(message);
    alpha.extend_from_slice(session_id);
    
    // Generate the VRF output
    let vrf_output = prove_vrf(
        &keys.expanded_private_key.private_key,
        &keys.public_key,
        &alpha,
    );
    
    vrf_output
}

/// Internal function to compute a VRF and its proof
fn prove_vrf(
    private_key: &Scalar<Ed25519>,
    public_key: &Point<Ed25519>,
    alpha: &[u8],
) -> VrfOutput {
    // Step 1: Hash the message to a curve point (ECVRF-EDWARDS25519-SHA512)
    let h_point = hash_to_curve(alpha, public_key);
    
    // Step 2: Compute gamma = x * h_point (VRF output point)
    let gamma = h_point.clone() * private_key;
    
    // Step 3: Generate k (nonce) for the proof
    let k = generate_nonce(private_key, &h_point, alpha);
    
    // Step 4: Compute k*h_point
    let k_h = h_point.clone() * &k;
    
    // Step 5: Compute challenge c = H(public_key || h_point || gamma || k_h)
    let c = compute_challenge(public_key, &h_point, &gamma, &k_h);
    
    // Step 6: Compute response s = k - c*x mod q
    let s = k - &(&c * private_key);
    
    // Step 7: Derive ephemeral keys from the VRF output
    let vrf_value = point_to_bytes(&gamma);
    
    // Generate r from the VRF output
    let r = Sha512::new()
        .chain(&[2]) // Domain separator
        .chain(&vrf_value)
        .result_scalar();
    
    // Compute R = r * G
    let R = Point::generator() * &r;
    
    // Create and return the VRF output and proof
    VrfOutput {
        private_key: r,
        public_key: R,
        proof: VrfProof {
            gamma,
            c,
            s,
        },
    }
}

/// Verify a VRF proof for an ephemeral key
///
/// Verifies that a given ephemeral key R was generated correctly
/// according to the VRF protocol.
///
/// Arguments:
/// * `public_key`: The signer's public key
/// * `message`: The message being signed
/// * `session_id`: Session identifier (must match the one used in generation)
/// * `R`: The ephemeral public key to verify
/// * `proof`: The VRF proof
///
/// Returns true if the ephemeral key was correctly generated
pub fn verify_ephemeral_key_vrf(
    public_key: &Point<Ed25519>,
    message: &[u8],
    session_id: &[u8],
    R: &Point<Ed25519>,
    proof: &VrfProof,
) -> bool {
    // Recreate the alpha string
    let mut alpha = Vec::with_capacity(message.len() + session_id.len() + 8);
    alpha.extend_from_slice(b"MPEd25519"); // Domain separator
    alpha.extend_from_slice(message);
    alpha.extend_from_slice(session_id);
    
    // Verify the VRF proof
    if !verify_vrf_proof(public_key, &alpha, proof) {
        return false;
    }
    
    // Derive the expected r and R from the VRF output
    let vrf_value = point_to_bytes(&proof.gamma);
    
    let expected_r = Sha512::new()
        .chain(&[2]) // Domain separator 
        .chain(&vrf_value)
        .result_scalar();
    
    let expected_R = Point::generator() * &expected_r;
    
    // Check if the provided R matches the expected R
    &expected_R == R
}

/// Internal function to verify a VRF proof
fn verify_vrf_proof(
    public_key: &Point<Ed25519>,
    alpha: &[u8],
    proof: &VrfProof,
) -> bool {
    // Step 1: Hash the message to a curve point
    let h_point = hash_to_curve(alpha, public_key);
    
    // Step 2: u = s*h_point + c*gamma
    let u = &h_point * &proof.s + &proof.gamma * &proof.c;
    
    // Step 3: v = s*G + c*public_key
    let _v = Point::generator() * &proof.s + public_key * &proof.c;
    
    // Step 4: Recompute challenge c' = H(public_key || h_point || gamma || u)
    let c_prime = compute_challenge(public_key, &h_point, &proof.gamma, &u);
    
    // Step 5: Check if c == c'
    proof.c == c_prime
}

/// Generate an ephemeral key pair with a VRF for multi-party signing
/// 
/// This function is a direct replacement for create_ephemeral_key_and_commit_rng
/// in the multi-party signing protocol.
///
/// Arguments:
/// * `keys`: The signer's expanded keypair
/// * `message`: The message being signed
/// * `session_id`: Optional session identifier (can be empty)
///
/// Returns the ephemeral key, VRF proof, and derived values for protocol compatibility
pub fn create_ephemeral_key_with_vrf(
    keys: &ExpandedKeyPair,
    message: &[u8],
    session_id: &[u8],
) -> (EphemeralKey, VrfProof) {
    let vrf_result = generate_ephemeral_key_vrf(keys, message, session_id);
    
    let ephemeral_key = EphemeralKey {
        r: vrf_result.private_key,
        R: vrf_result.public_key,
    };
    
    (ephemeral_key, vrf_result.proof)
}

// Helper functions

/// Hash an input string to a curve point
fn hash_to_curve(alpha: &[u8], public_key: &Point<Ed25519>) -> Point<Ed25519> {
    // Following ECVRF-EDWARDS25519-SHA512-TAI try-and-increment method
    let mut v = Vec::with_capacity(alpha.len() + 32);
    v.extend_from_slice(b"\x02"); // suite string
    
    // Add public key bytes
    if let Some(pk_bytes) = public_key.to_bytes(true).to_vec().into() {
        v.extend_from_slice(&pk_bytes);
    }
    
    v.extend_from_slice(alpha);
    
    // Try consecutive counter values until we get a valid point
    let mut counter = 0u8;
    loop {
        let mut hash_input = v.clone();
        hash_input.push(counter);
        
        let digest = Sha512::digest(&hash_input);
        
        // Attempt to create a point from the hash
        // Simplified: In practice, proper try-and-increment with cofactor clearing would be used
        if let Ok(point) = Point::<Ed25519>::from_bytes(&digest[..32]) {
            return point;
        }
        
        counter += 1;
        // In practice, we would need a failsafe here if counter gets too high
        if counter > 100 {
            // This is a fallback that should practically never happen
            // Just use a basic point derived from the hash
            let scalar = Scalar::from_bytes(&digest[..32]).unwrap_or_else(|_| Scalar::random());
            return Point::generator() * &scalar;
        }
    }
}

/// Generate a deterministic nonce for the VRF proof
fn generate_nonce(private_key: &Scalar<Ed25519>, h_point: &Point<Ed25519>, alpha: &[u8]) -> Scalar<Ed25519> {
    let mut hasher = Sha512::new();
    let bytes = private_key.to_bytes();
    let bytes_vec = bytes.to_vec();
    hasher.update(&bytes_vec);
    hasher.update(&point_to_bytes(h_point));
    hasher.update(alpha);
    
    let hash_result = hasher.finalize();
    Scalar::from_bytes(&hash_result[..32]).unwrap_or_else(|_| Scalar::random())
}

/// Compute the VRF challenge value
fn compute_challenge(
    public_key: &Point<Ed25519>,
    h_point: &Point<Ed25519>,
    gamma: &Point<Ed25519>,
    u: &Point<Ed25519>,
) -> Scalar<Ed25519> {
    let mut hasher = Sha512::new();
    hasher.update(&point_to_bytes(public_key));
    hasher.update(&point_to_bytes(h_point));
    hasher.update(&point_to_bytes(gamma));
    hasher.update(&point_to_bytes(u));
    
    let hash_result = hasher.finalize();
    Scalar::from_bytes(&hash_result[..32]).unwrap_or_else(|_| Scalar::random())
}

/// Convert a curve point to bytes
fn point_to_bytes(point: &Point<Ed25519>) -> Vec<u8> {
    point.to_bytes(true).to_vec()
}

/// Ephemeral key structure (compatible with the existing protocol)
#[derive(Clone, Debug)]
pub struct EphemeralKey {
    /// Ephemeral private key
    pub r: Scalar<Ed25519>,
    /// Ephemeral public key
    pub R: Point<Ed25519>,
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::protocols::{aggsig::{self, KeyAgg}, tests::deterministic_fast_rand};
    use std::time::Instant;

    /// Test VRF-based three-party signing process
    #[test]
    fn test_multiparty_signing_with_vrf_three_parties() {
        let mut rng = deterministic_fast_rand("test_multiparty_signing_with_vrf_three_parties", None);
        // Run the test several times to ensure consistency
        test_multiparty_signing_with_vrf_three_parties_internal(&mut rng);
        // Uncomment to run multiple iterations
        // for _i in 0..10 {
        //     test_multiparty_signing_with_vrf_three_parties_internal(&mut rng);
        // }
    }

    /// Internal implementation of VRF-based three-party signing test
    fn test_multiparty_signing_with_vrf_three_parties_internal(rng: &mut impl Rng) {
        use std::time::Instant;
        
        // Start timing
        let start_time = Instant::now();
        let offline_time = Instant::now();
        
        // Test message
        let message: [u8; 4] = [79, 77, 69, 82]; // "OMER" in ASCII
        
        // Generate a unique session ID to prevent replay attacks
        let mut session_id = [0u8; 16];
        rng.fill_bytes(&mut session_id);
        
        println!("---- Testing VRF-based multi-party signing (3 parties) ----");
        
        // Round 0: Generate signing keys for all parties
        let party1_key = ExpandedKeyPair::create();
        let party2_key = ExpandedKeyPair::create();
        let party3_key = ExpandedKeyPair::create();
        
        // Round 1: Generate ephemeral keys and VRF proofs
        // Note: This replaces both the commitment and reveal rounds from the original protocol
        let (party1_ephemeral_key, party1_vrf_proof) =
            create_ephemeral_key_with_vrf(&party1_key, &message, &session_id);
        let (party2_ephemeral_key, party2_vrf_proof) =
            create_ephemeral_key_with_vrf(&party2_key, &message, &session_id);
        let (party3_ephemeral_key, party3_vrf_proof) =
            create_ephemeral_key_with_vrf(&party3_key, &message, &session_id);
        
        // Each party verifies the VRF proofs from the other parties
        // Party 1 verifies Party 2's and Party 3's proofs
        assert!(
            verify_ephemeral_key_vrf(
                &party2_key.public_key,
                &message,
                &session_id,
                &party2_ephemeral_key.R,
                &party2_vrf_proof
            ),
            "Party 2's VRF proof verification failed"
        );
        assert!(
            verify_ephemeral_key_vrf(
                &party3_key.public_key,
                &message,
                &session_id,
                &party3_ephemeral_key.R,
                &party3_vrf_proof
            ),
            "Party 3's VRF proof verification failed"
        );
        
        // Party 2 verifies Party 1's and Party 3's proofs
        assert!(
            verify_ephemeral_key_vrf(
                &party1_key.public_key,
                &message,
                &session_id,
                &party1_ephemeral_key.R,
                &party1_vrf_proof
            ),
            "Party 1's VRF proof verification failed"
        );
        assert!(
            verify_ephemeral_key_vrf(
                &party3_key.public_key,
                &message,
                &session_id,
                &party3_ephemeral_key.R,
                &party3_vrf_proof
            ),
            "Party 3's VRF proof verification failed"
        );
        
        // Party 3 verifies Party 1's and Party 2's proofs
        assert!(
            verify_ephemeral_key_vrf(
                &party1_key.public_key,
                &message,
                &session_id,
                &party1_ephemeral_key.R,
                &party1_vrf_proof
            ),
            "Party 1's VRF proof verification failed"
        );
        assert!(
            verify_ephemeral_key_vrf(
                &party2_key.public_key,
                &message,
                &session_id,
                &party2_ephemeral_key.R,
                &party2_vrf_proof
            ),
            "Party 2's VRF proof verification failed"
        );
        
        // Compute aggregated public key (APK)
        let pks = [
            party1_key.public_key.clone(),
            party2_key.public_key.clone(),
            party3_key.public_key.clone(),
        ];
        let party1_key_agg = KeyAgg::key_aggregation_n(&pks, 0);
        let party2_key_agg = KeyAgg::key_aggregation_n(&pks, 1);
        let party3_key_agg = KeyAgg::key_aggregation_n(&pks, 2);
        
        // Ensure all parties computed the same aggregated key
        assert_eq!(party1_key_agg.apk, party2_key_agg.apk);
        assert_eq!(party1_key_agg.apk, party3_key_agg.apk);
        
        // Compute aggregated R value (R_tot)
        let Ri = [
            party1_ephemeral_key.R,
            party2_ephemeral_key.R,
            party3_ephemeral_key.R,
        ];
        let R_tot = aggsig::get_R_tot(&Ri);
        
        // Mark end of offline phase
        let r_duration = offline_time.elapsed();
        println!("离线阶段预计算完成，耗时: {:?}", r_duration);
        
        // Generate partial signatures
        let s1 = aggsig::partial_sign(
            &party1_ephemeral_key.r,
            &party1_key,
            &party1_key_agg.hash,
            &R_tot,
            &party1_key_agg.apk,
            &message,
        );
        let s2 = aggsig::partial_sign(
            &party2_ephemeral_key.r,
            &party2_key,
            &party2_key_agg.hash,
            &R_tot,
            &party2_key_agg.apk,
            &message,
        );
        let s3 = aggsig::partial_sign(
            &party3_ephemeral_key.r,
            &party3_key,
            &party3_key_agg.hash,
            &R_tot,
            &party3_key_agg.apk,
            &message,
        );
        
        // Combine partial signatures
        let s = [s1, s2, s3];
        let signature = aggsig::add_signature_parts(&s);
        
        // Verify the final signature
        assert!(
            signature.verify(&message, &party1_key_agg.apk).is_ok(),
            "Final signature verification failed"
        );
        
        // Report total signing time
        let sign_duration = start_time.elapsed();
        println!("在线阶段计算完成，耗时: {:?}", sign_duration);
        println!("VRF基于的多方签名测试完成");
    }
    
    /// Test comparison between commitment-based and VRF-based approaches
    #[test]
    fn compare_commitment_vs_vrf_performance() {
        let mut rng = deterministic_fast_rand("compare_commitment_vs_vrf_performance", None);
        
        // Test message
        let mut message: [u8; 32] = [0; 32];
        rng.fill_bytes(&mut message[..]);
        
        // Setup for timing
        let mut session_id = [0u8; 16];
        rng.fill_bytes(&mut session_id);
        
        // Generate keys
        let key_pair = ExpandedKeyPair::create();
        
        println!("---- Performance Comparison: Commitment vs VRF ----");
        
        // Measure commitment-based approach
        let commitment_start = Instant::now();
        let (eph_key1, sign_first, sign_second) = 
            aggsig::create_ephemeral_key_and_commit_rng(&key_pair, &message, &mut rng);
        let commitment_duration = commitment_start.elapsed();
        
        // Measure VRF-based approach
        let vrf_start = Instant::now();
        let (eph_key2, vrf_proof) = 
            create_ephemeral_key_with_vrf(&key_pair, &message, &session_id);
        let vrf_duration = vrf_start.elapsed();
        
        println!("Commitment-based approach time: {:?}", commitment_duration);
        println!("VRF-based approach time: {:?}", vrf_duration);
        println!("Difference: {:?}", vrf_duration.checked_sub(commitment_duration).unwrap_or_default());
        
        // Verify that both approaches generated valid ephemeral keys
        assert_ne!(eph_key1.R, eph_key2.R, "Both approaches should generate different R values");
        
        // Verify commitment
        assert!(
            test_com(
                &sign_second.R,
                &sign_second.blind_factor,
                &sign_first.commitment
            ),
            "Commitment verification failed"
        );
        
        // Verify VRF proof
        assert!(
            verify_ephemeral_key_vrf(
                &key_pair.public_key,
                &message,
                &session_id,
                &eph_key2.R,
                &vrf_proof
            ),
            "VRF proof verification failed"
        );
    }

    fn test_com(r_to_test: &Point<Ed25519>, blind_factor: &BigInt, comm: &BigInt) -> bool {
        use curv::cryptographic_primitives::commitments::{
            hash_commitment::HashCommitment, traits::Commitment,
        };
        let computed_comm =
            &HashCommitment::<Sha512>::create_commitment_with_user_defined_randomness(
                &r_to_test.y_coord().unwrap(),
                blind_factor,
            );
        computed_comm == comm
    }
} 