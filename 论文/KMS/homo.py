from tinyec import registry, ec
from phe import paillier
import hashlib
import secrets

# 1. 初始化椭圆曲线参数
curve = registry.get_curve('secp256k1')
p = int(curve.field.p)  # 椭圆曲线阶数

# 2. 生成Paillier同态加密密钥对
keypair = paillier.generate_paillier_keypair(n_length=2048)
pub_key, priv_key = keypair

# 3. 定义参与者类
class Participant:
    def __init__(self, id):
        self.id = id
        self.k_share = secrets.randbelow(p)  # 本地生成的k份额
        self.encrypted_share = pub_key.encrypt(self.k_share)  # 加密后的份额

# 4. 模拟三个参与者
participants = [Participant(i) for i in range(3)]

# 5. 聚合加密的k份额（利用Paillier加法同态性）
def aggregate_shares(participants):
    aggregated = participants[0].encrypted_share
    for p in participants[1:]:
        aggregated += p.encrypted_share
    return aggregated

encrypted_k = aggregate_shares(participants)

# 6. 解密得到最终的k值（模p运算）
def decrypt_k(encrypted, priv_key):
    k = priv_key.decrypt(encrypted) % p
    if k == 0:  # 防止k为0的情况
        raise ValueError("Invalid k value: 0")
    return k

k = decrypt_k(encrypted_k, priv_key)

# 7. 生成ECDSA签名（示例部分）
def ecdsa_sign(message, k, private_key):
    # 计算R = k*G
    G = curve.g
    R = k * G
    r = R.x % p
    
    # 计算哈希
    h = int.from_bytes(hashlib.sha256(message).digest(), 'big') % p
    
    # 计算s = (h + r*private_key) / k mod p
    s = (pow(k, -1, p) * (h + r * private_key)) % p
    return (r, s)

# 测试签名验证
def ecdsa_verify(message, signature, public_key):
    r, s = signature
    if not (0 < r < p and 0 < s < p):
        return False
    
    h = int.from_bytes(hashlib.sha256(message).digest(), 'big') % p
    w = pow(s, -1, p)
    u1 = (h * w) % p
    u2 = (r * w) % p
    
    G = curve.g
    P = public_key
    R = u1 * G + u2 * P
    return R.x == r

# 使用示例
if __name__ == "__main__":
    # 生成测试密钥对
    private_key = secrets.randbelow(p)
    public_key = private_key * curve.g
    
    # 签名消息
    message = b"Hello, Threshold ECDSA!"
    signature = ecdsa_sign(message, k, private_key)
    
    # 验证签名
    print("Signature valid:", ecdsa_verify(message, signature, public_key))
