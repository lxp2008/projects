from ecdsa import SigningKey, VerifyingKey, SECP256k1, util
import secrets
import hashlib
import binascii

class ThresholdECDSA:
    def __init__(self, threshold=2, total_nodes=3):
        self.threshold = threshold
        self.total_nodes = total_nodes
        self.curve = SECP256k1
        self.G = self.curve.generator
        self.order = self.curve.order
    
    def generate_shares(self):
        """生成私钥分片（Shamir秘密共享）"""
        self.private_key = SigningKey.generate(curve=self.curve)
        K = self.private_key.privkey.secret_multiplier
        coeffs = [K] + [secrets.randbelow(self.order) for _ in range(self.threshold-1)]
        shares = {}
        for i in range(1, self.total_nodes+1):
            x = i
            y = sum(coeff * (x**power) for power, coeff in enumerate(coeffs)) % self.order
            shares[x] = y
        return shares
    
    def generate_shared_k(self, participants):
        """生成临时密钥k的分片（统一多项式）"""
        coeffs_k = [secrets.randbelow(self.order) for _ in range(self.threshold)]
        k_shares = {}
        for i in participants:
            x = i
            y = sum(coeff * (x**power) for power, coeff in enumerate(coeffs_k)) % self.order
            k_shares[x] = y
        return k_shares
    
    def partial_sign(self, message, node_id, share_ki, k_shares):
        """生成部分签名（使用完整的k分片集合）"""
        k_combined = self._combine_shares(k_shares)
        r = (k_combined * self.G).x() % self.order
        if r == 0:
            raise ValueError("Invalid r (zero)")
        
        e = self._hash_message(message)
        k_inv = pow(k_combined, -1, self.order)
        s_i = (e + r * share_ki) * k_inv % self.order
        return (r, s_i, node_id)
    
    def aggregate_signature(self, partials):
        """聚合签名（强制r一致性检查）"""
        if len(partials) < self.threshold:
            raise ValueError("分片不足")
        
        r_values = {p[0] for p in partials}
        if len(r_values) != 1:
            raise ValueError(f"不一致的r值: {r_values}")
        r = r_values.pop()
        
        node_ids = [p[2] for p in partials]
        s_shares = [p[1] for p in partials]
        
        s = 0
        for i in range(len(node_ids)):
            xi = node_ids[i]
            lambda_i = self._lagrange_coeff(xi, node_ids)
            s += s_shares[i] * lambda_i
        s = s % self.order
        
        return util.sigencode_der(r, s, self.order)
    
    def _lagrange_coeff(self, xi, all_x):
        """计算拉格朗日系数（严格模运算）"""
        numerator, denominator = 1, 1
        for xj in all_x:
            if xi != xj:
                numerator = (numerator * (-xj)) % self.order
                denominator = (denominator * (xi - xj)) % self.order
        denominator_inv = pow(denominator, -1, self.order)
        return (numerator * denominator_inv) % self.order
    
    def _combine_shares(self, shares):
        """组合分片（确保至少t个分片）"""
        if len(shares) < self.threshold:
            raise ValueError("分片不足，无法恢复k")
        secret = 0
        x = list(shares.keys())
        for xi in x:
            lambda_i = self._lagrange_coeff(xi, x)
            secret = (secret + shares[xi] * lambda_i) % self.order
        return secret
    
    def _hash_message(self, message):
        """哈希消息（符合ECDSA标准）"""
        h = hashlib.sha256(message).digest()
        return int.from_bytes(h, byteorder='big') % self.order

def main():
    tes = ThresholdECDSA(threshold=2, total_nodes=3)
    
    # 生成私钥分片
    shares = tes.generate_shares()
    pubkey = tes.private_key.get_verifying_key()
    print(f"公钥: {pubkey.to_string().hex()}")
    
    # 生成共享的k分片（所有节点使用同一多项式）
    participants = [1, 2]
    k_shares = tes.generate_shared_k(participants)
    
    # 生成部分签名（传递完整的k分片集合）
    message = b"Hello Threshold ECDSA!"
    partial1 = tes.partial_sign(message, 1, shares[1], k_shares)
    partial2 = tes.partial_sign(message, 2, shares[2], k_shares)
    
    # 聚合签名
    try:
        der_sig = tes.aggregate_signature([partial1, partial2])
        print(f"\nDER签名: {binascii.hexlify(der_sig).decode()}")
    except ValueError as e:
        print("聚合失败:", e)
        return
    
    # 验证签名
    try:
        is_valid = pubkey.verify(
            der_sig,
            message,
            hashfunc=hashlib.sha256,
            sigdecode=util.sigdecode_der
        )
        print("验证结果:", "成功 ✅" if is_valid else "失败 ❌")
    except Exception as e:
        print("验证异常:", e)
    
    # 对比标准签名
    standard_sk = SigningKey.from_string(tes.private_key.to_string(), curve=SECP256k1)
    standard_sig = standard_sk.sign(message, hashfunc=hashlib.sha256)
    print(f"\n标准签名: {binascii.hexlify(standard_sig).decode()}")

if __name__ == "__main__":
    main()