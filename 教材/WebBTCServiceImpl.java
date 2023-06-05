package com.yougong.web.modules.chain.service.impl;

import cn.hutool.core.util.HexUtil;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpUtil;
import com.alibaba.fastjson.JSONArray;
import com.yougong.common.api.ResultCode;
import com.yougong.common.exception.BizException;
import com.yougong.web.modules.chain.model.dto.BuyCoinDTO;
import com.yougong.web.modules.chain.model.dto.ListUnspentDTO;
import com.yougong.web.modules.chain.model.dto.SendRawTransactionDTO;
import com.yougong.web.modules.chain.model.dto.SendTransactionDTO;
import com.yougong.web.modules.chain.model.dto.rust.BTCSignHashDTO;
import com.yougong.web.modules.chain.model.dto.rust.BTCSignatureDTO;
import com.yougong.web.modules.chain.model.dto.rust.BTCTransaction;
import com.yougong.web.modules.chain.model.enums.ChainTypeEnum;
import com.yougong.web.modules.chain.model.enums.TransactionStateEnum;
import com.yougong.common.exception.ApiException;
import com.yougong.common.util.JsonUtils;
import com.yougong.common.util.StringUtils;
import com.yougong.web.modules.chain.model.enums.ErrorEnum;
import com.yougong.web.modules.chain.model.enums.TransactionTypeEnum;
import com.yougong.web.modules.chain.model.pojo.Transaction;
import com.yougong.web.modules.chain.model.vo.SendTransactionVO;
import com.yougong.web.modules.chain.model.vo.SignTransactionVO;
import com.yougong.web.modules.chain.model.vo.TransactionRetracementVO;
import com.yougong.web.modules.chain.model.vo.UnspentVO;
import com.yougong.web.modules.chain.properties.ThirdPartyServiceConfig;
import com.yougong.web.modules.chain.service.WebService;
import lombok.extern.slf4j.Slf4j;
import org.bitcoinj.core.*;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.params.TestNet3Params;
import org.bitcoinj.script.Script;
import org.bouncycastle.util.encoders.Hex;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import wf.bitcoin.javabitcoindrpcclient.BitcoinJSONRPCClient;
import wf.bitcoin.javabitcoindrpcclient.BitcoindRpcClient;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.MalformedURLException;
import java.util.*;


/**
 * @author kirk
 * @description Web3j 接口服务
 * @createDate 2022-04-28 11:12:42
 */
@Slf4j
@Service("webBTCServiceImpl")
public class WebBTCServiceImpl implements WebService {

    /**
     * account = "";时是默认账户
     */
    @Autowired
    ThirdPartyServiceConfig thirdPartyServiceConfig;

    protected NetworkParameters getNetworkParameters() {
        if (thirdPartyServiceConfig.getProd()) {
            //正式环境
            return MainNetParams.get();
            //测试链
        }
        return TestNet3Params.get();
    }

    private volatile BitcoinJSONRPCClient bitcoinClient = null;

    @Override
    public BitcoinJSONRPCClient getBitcoinClient() {
        if (bitcoinClient == null) {
            synchronized (this) {
                if (bitcoinClient == null) {
                    try {
                        bitcoinClient = new BitcoinJSONRPCClient(this.getUrl());
                    } catch (MalformedURLException e) {
                        throw new ApiException("获取 bitcoinClient 失败,url:{}", this.getUrl());
                    }
                }
            }
        }
        return bitcoinClient;
    }

    @Override
    public String getChainType() {
        return "BTC";
    }

    @Override
    public String getUrl() {
        return thirdPartyServiceConfig.getBTCUrl();
    }

    @Override
    public String getTestAccount() {
        //"" 为默认钱包
        /*String account = "";
        List<String> addressesByAccount = getBitcoinClient().getAddressesByAccount(account);
        String[] addresses = addressesByAccount.toArray(new String[0]);
        List<BitcoindRpcClient.Unspent> unspents = getBitcoinClient().listUnspent(1, 9999999, addresses);
        for (BitcoindRpcClient.Unspent unspent : unspents) {
            BigDecimal amount = unspent.amount();
            if (amount.compareTo(new BigDecimal("50")) >= 0) {
                return unspent.address();
            }
        }
        throw new ApiException(ErrorEnum.NOT_FOUND_ACCOUNT);*/
        return "bcrt1qdkvjldpcxgje6fpktqclf9dh6jnl8k4x4nm03a";
    }

    @Override
    public String sendTransaction(BuyCoinDTO buyCoinDTO) {
        // 节点默认钱包向指定地址发送比特币
        String txId = getBitcoinClient().sendFrom("", buyCoinDTO.getTo(), new BigDecimal(buyCoinDTO.getValue()));
        log.info("节点默认钱包向指定地址发送比特币:{}", txId);
        //发送比特币后交易不会里面确认，就要进行挖矿记账才能确认这笔交易,用默认钱包进行挖矿
        List<String> generate = getBitcoinClient().generate(1);
        log.info("生成block:{}", generate.toString());
        return txId;
    }


    @Override
    public String sendRawTransaction(SendRawTransactionDTO rawTransactionDTO) {
        return this.getBitcoinClient().sendRawTransaction(rawTransactionDTO.getSignHash());
    }

    @Override
    public boolean validateAddress(String addressStr) {
        //验证btc地址是否合法
        Address address = null;
        try {
            address = Address.fromString(getNetworkParameters(), addressStr);
        } catch (AddressFormatException e) {
            throw new ApiException(ErrorEnum.NOT_FOUND_ACCOUNT);
        }
        return Objects.nonNull(address);
    }

    @Override
    public BigInteger getBalance(String walletAddress) {
        // 查询钱包UTXO 返回归属于本钱包的未消费交易输出数组
        List<BitcoindRpcClient.Unspent> unspents = getBitcoinClient().listUnspent(1, 9999999, walletAddress);
        BigDecimal sum = new BigDecimal(0);
        for (BitcoindRpcClient.Unspent unspent : unspents) {
            sum = sum.add(unspent.amount());
        }
        //satoshis：BTC 比率是 1：100000000
        sum = sum.multiply(BigDecimal.TEN.pow(8));
        return sum.toBigInteger();
    }

    @Override
    public String getBalanceStr(String walletAddress) {
        // 查询钱包UTXO 返回归属于本钱包的未消费交易输出数组
        List<BitcoindRpcClient.Unspent> unspents = getBitcoinClient().listUnspent(1, 9999999, walletAddress);
        BigDecimal sum = new BigDecimal(0);
        for (BitcoindRpcClient.Unspent unspent : unspents) {
            sum = sum.add(unspent.amount());
        }
        return sum.stripTrailingZeros().toPlainString();
    }

    @Override
    public List<UnspentVO> getListUnspent(ListUnspentDTO listUnspentDTO) {
        List<UnspentVO> unspentVOS = new ArrayList<>();
        List<BitcoindRpcClient.Unspent> unspents = getBitcoinClient().listUnspent(listUnspentDTO.getMinConf(), listUnspentDTO.getMaxConf(), listUnspentDTO.getAddresses());
        for (BitcoindRpcClient.Unspent unspent : unspents) {
            UnspentVO unspentVO = new UnspentVO();
            unspentVO.setAddress(unspent.address());
            unspentVO.setTxId(unspent.txid());
            unspentVO.setAmount(unspent.amount());
            unspentVO.setScriptPubKey(unspent.scriptPubKey());
            unspentVO.setVout(unspent.vout());
            unspentVOS.add(unspentVO);
        }
        return unspentVOS;
    }

    @Override
    public TransactionRetracementVO getTransactionRetracement(String txId) {
        TransactionRetracementVO vo = new TransactionRetracementVO();
        vo.setState(TransactionStateEnum.TRADING.getCode());
        try {
            if (StringUtils.isNotBlank(txId)) {
                HashMap<String, Object> params = new HashMap<>();
                params.put("id", "1");
                params.put("method", "gettransaction");
                params.put("params", Arrays.asList(txId));
                HttpResponse response = HttpUtil.createPost("http://129.211.222.170:18543")
                        .header("Authorization", "Basic YWRtaW4xOjEyMw==")
                        .body(JsonUtils.toJson(params))
                        .execute();
                if (!response.isOk()) {
                    throw new ApiException("http 请求错误");
                }
                Map<String, Object> resultMap = JsonUtils.fromJson(response.body());
                if (Objects.nonNull(resultMap.get("error"))) {
                    throw new ApiException("获取交易失败");
                }
                String blockHash = Optional.ofNullable(resultMap.get("result"))
                        .map(v -> (Map<String, Object>) v)
                        .map(v -> v.get("blockhash"))
                        .map(String::valueOf).orElse(null);
                Integer blockHeight = Optional.ofNullable(resultMap.get("result"))
                        .map(v -> (Map<String, Object>) v)
                        .map(v -> v.get("blockheight"))
                        .map(String::valueOf)
                        .map(Integer::valueOf).orElse(null);
                //BitcoindRpcClient.RawTransaction rawTransaction = this.getBitcoinClient().getRawTransaction(txId);
                if (Objects.nonNull(blockHash)) {
                    vo.setBlockHash(blockHash);
                    //BitcoindRpcClient.Block block = this.getBitcoinClient().getBlock(blockHash);
                    vo.setBlock(BigInteger.valueOf(blockHeight));
                    vo.setState(TransactionStateEnum.DONE.getCode());
                }
            }
        } catch (Exception e) {
            log.debug(getChainType() + "获取交易回值信息失败txId={},异常={}", txId, e.getMessage(), e);
            throw new ApiException(ErrorEnum.TRANSACTION_RETRACEMENT_FAILED);
        }
        return vo;
    }

    @Override
    public boolean isNetWorkOK() {
        try {
            BitcoindRpcClient.NetworkInfo networkInfo = getBitcoinClient().getNetworkInfo();
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    @Override
    public String generateAddress(String pubKeyHexStr) {
        ECKey ecKey = ECKey.fromPublicOnly(Hex.decode(pubKeyHexStr));
        return Address.fromKey(getNetworkParameters(), ecKey, Script.ScriptType.P2PKH).toString();
    }

    @Override
    public boolean importAddress(String addr) {
        String account = "dacs";
        //导入 mpc私钥 生成的地址
        Object result = null;
        try {
            result = getBitcoinClient().importAddress(addr, account, false);
            log.info("{}导入地址,addr:{},result：{}", this.getChainType(), addr, JsonUtils.toJson(result));
        } catch (Exception e) {
            log.error("{}导入地址失败,addr:{}", this.getChainType(), addr);
            return false;
        }
        return true;
    }

    @Override
    public Boolean getTransactionInformation(String txId) {
        Boolean flag = false;
        try {
            this.getBitcoinClient().getRawTransaction(txId);
            flag = true;
        } catch (Exception e) {
            log.error(getChainType() + "获取链上交易信息失败txId={},异常={}", txId, e.getMessage(), e);
            //throw new ApiException(ErrorEnum.ON_CHAIN_INFORMATION_TRADE_FAILED);
        }
        return flag;
    }

    @Override
    public String generate(Integer num) {
        return getBitcoinClient().generate(num).toString();
    }

    @Override
    public BitcoindRpcClient.Block getBlock(String txId) {
        return getBitcoinClient().getBlock(txId);
    }

    @Override
    public SendTransactionVO sendTransaction(SendTransactionDTO transaction) {
        SendTransactionVO vo = SendTransactionVO.failed();
        try {
            NetworkParameters networkParameters = getNetworkParameters();
            //发送交易
            List<BTCSignatureDTO> rustSignRests = transaction.getBtcSignatureDTOList();
            BTCTransaction btcTransaction = new BTCTransaction(networkParameters);
            String gasJson = transaction.getGasJson();
            JSONArray jsonArray = JSONArray.parseArray(gasJson);
            for (int i = 0; i < jsonArray.size(); i++) {
                btcTransaction.addOutput(Coin.valueOf(jsonArray.getJSONObject(i).getLong("coin")), Address.fromString(networkParameters, jsonArray.getJSONObject(i).getString("address")));
            }
            //输入未消费列表项
            btcTransaction.setPurpose(BTCTransaction.Purpose.USER_PAYMENT);
            for (BTCSignatureDTO rustSignRest : rustSignRests) {
                Sha256Hash of = Sha256Hash.wrap(rustSignRest.getUtxoHash());
                TransactionOutPoint outPoint = new TransactionOutPoint(networkParameters, rustSignRest.getIndex(), of);
                btcTransaction.addSignedInput(rustSignRest.getRs(), outPoint, transaction.getPubKey());
            }
            String hash = HexUtil.encodeHexStr(btcTransaction.bitcoinSerialize());
            String transactionHash = this.getBitcoinClient().sendRawTransaction(hash);
            log.info("链类型：{}, 交易hash:{}", this.getChainType(), transactionHash);
            //Regtest 需要自己挖矿
            if (ChainTypeEnum.BTC.getCode().equals(this.getChainType()) && !thirdPartyServiceConfig.getProd()) {
                //发送比特币后交易不会里面确认，就要进行挖矿记账才能确认这笔交易,用默认钱包进行挖矿
                List<String> generate = getBitcoinClient().generate(1);
                log.info("生成block:{}", generate.toString());
            }
            if (StringUtils.isNotBlank(transactionHash)) {
                vo.setFlag(true);
                vo.setTxId(transactionHash);
                return vo;
            }
        } catch (Exception e) {
            log.error("{},msg:{}", ErrorEnum.SEND_TRANSACTION_FAILD.getMessage(), e.getMessage());
            log.debug(e.getMessage(), e);
        }
        return vo;
    }

    @Override
    public Transaction fillTransaction(Transaction transaction) {
        NetworkParameters networkParameters = getNetworkParameters();
        String fromAddress = transaction.getFrom();
        String toAddress = transaction.getTo();
        BigInteger oldBalance = this.getBalance(transaction.getFrom());
        BigInteger gasPrice = null;
        if (transaction.getGasPriceStr() != null) {
            gasPrice = new BigDecimal(transaction.getGasPriceStr()).toBigInteger();
        } else {
            gasPrice = BigInteger.valueOf(102);
        }
        transaction.setGasPrice(gasPrice.longValue());
        // BitCoin Fee
        BigInteger fee = getFee(gasPrice.longValue(), transaction.getFrom(), transaction.getValue());
        log.info("{}手续费bigint:{}", this.getChainType(), fee.toString());
        //重置资产
        if (TransactionTypeEnum.RECOVER.getCode().equals(transaction.getType()) && Objects.isNull(transaction.getValue())) {
            //重置资产
            BigInteger valueBigInt = oldBalance.subtract(fee);
            String value = new BigDecimal(valueBigInt.toString()).divide(BigDecimal.TEN.pow(8)).toPlainString();
            transaction.setValue(value);
        } else if (TransactionTypeEnum.TRANSFER.getCode().equals(transaction.getType())) {
            //正常交易
            BigInteger amount = new BigDecimal(transaction.getValue()).multiply(BigDecimal.TEN.pow(8)).toBigInteger();
            BigInteger newBlance = oldBalance.subtract(amount).subtract(fee);
            if (!(newBlance.compareTo(BigInteger.ZERO) >= 0)) {
                if (!transaction.getFrom().equals(this.getTestAccount())) {
                    throw new BizException(ResultCode.CLIENT_RESOURCE_ACCOUNT_BALANCE_OVER);
                } else {
                    throw new BizException(ResultCode.CLIENT_RESOURCE_ACCOUNT_BALANCE_OVER1);
                }
            }
            List<UTXO> utxos = new ArrayList<>();
            List<BitcoindRpcClient.Unspent> unspents = getBitcoinClient().listUnspent(1, 9999999, fromAddress);
            for (BitcoindRpcClient.Unspent unspent : unspents) {
                BitcoindRpcClient.RawTransaction rawTx = getBitcoinClient().getRawTransaction(unspent.txid());
                BitcoindRpcClient.Block block = getBitcoinClient().getBlock(rawTx.blockHash());
                long satoshis = unspent.amount().multiply(BigDecimal.TEN.pow(8)).longValue();
                Script script = new Script(Utils.HEX.decode(unspent.scriptPubKey()));
                utxos.add(new UTXO(Sha256Hash.wrap(rawTx.txId()), unspent.vout(), Coin
                        .valueOf(satoshis), block.height(),
                        false, script, unspent.address()));
            }
            BTCTransaction btcTransaction = new BTCTransaction(networkParameters);
            String changeAddress = fromAddress;//找零地址
            Long changeAmount = 0L;
            Long utxoAmount = 0L;
            List<UTXO> needUtxos = new ArrayList<>();
            //获取未消费列表
            if (utxos == null || utxos.size() == 0) {
                throw new BizException(ResultCode.CLIENT_RESOURCE);
            }
            //遍历未花费列表，组装合适的item
            for (UTXO utxo : utxos) {
                if (utxoAmount >= (amount.longValue() + fee.longValue())) {
                    break;
                } else {
                    needUtxos.add(utxo);
                    utxoAmount += utxo.getValue().value;
                }
            }
            btcTransaction.addOutput(Coin.valueOf(amount.longValue()), Address.fromString(networkParameters, toAddress));
            ArrayList<HashMap<String, String>> list = new ArrayList<>();
            HashMap<String, String> hashMap = new HashMap<>();
            hashMap.put("coin", amount.toString());
            hashMap.put("address", toAddress);
            list.add(hashMap);
            //消费列表总金额 - 已经转账的金额 - 手续费 就等于需要返回给自己的金额了
            changeAmount = utxoAmount - (amount.longValue() + fee.longValue());
            //余额判断
            if (changeAmount < 0) {
                throw new BizException(ResultCode.CLIENT_RESOURCE_ACCOUNT_BALANCE_OVER);
            }
            //输出-转给自己(找零)
            if (changeAmount > 0) {
                btcTransaction.addOutput(Coin.valueOf(changeAmount), Address.fromString(networkParameters, changeAddress));
                HashMap<String, String> hashMap2 = new HashMap<>();
                hashMap2.put("coin", changeAmount.toString());
                hashMap2.put("address", changeAddress);
                list.add(hashMap2);
            }
            String string = JSONArray.toJSONString(list);
            transaction.setGasJson(string);
        }

        return transaction;
    }

    @Override
    public SignTransactionVO getSignTransactionDTO(Transaction transaction) {
        NetworkParameters networkParameters = getNetworkParameters();
        List<UTXO> utxos = new ArrayList<>();
        List<BitcoindRpcClient.Unspent> unspents = getBitcoinClient().listUnspent(1, 9999999, transaction.getFrom());
        for (BitcoindRpcClient.Unspent unspent : unspents) {
            BitcoindRpcClient.RawTransaction rawTx = getBitcoinClient().getRawTransaction(unspent.txid());
            BitcoindRpcClient.Block block = getBitcoinClient().getBlock(rawTx.blockHash());
            long satoshis = unspent.amount().multiply(BigDecimal.TEN.pow(8)).longValue();
            Script script = new Script(Utils.HEX.decode(unspent.scriptPubKey()));
            utxos.add(new UTXO(Sha256Hash.wrap(rawTx.txId()), unspent.vout(), Coin.valueOf(satoshis), block.height(), false, script, unspent.address()));
        }
        BTCTransaction btcTransaction = new BTCTransaction(networkParameters);
        //获取未消费列表
        if (utxos == null || utxos.size() == 0) {
            throw new BizException(ResultCode.CLIENT_RESOURCE);
        }
        String gasJson = transaction.getGasJson();
        JSONArray jsonArray = JSONArray.parseArray(gasJson);
        for (int i = 0; i < jsonArray.size(); i++) {
            btcTransaction.addOutput(Coin.valueOf(jsonArray.getJSONObject(i).getLong("coin")), Address.fromString(networkParameters, jsonArray.getJSONObject(i).getString("address")));
        }
        //输入未消费列表项
        btcTransaction.setPurpose(BTCTransaction.Purpose.USER_PAYMENT);
        ArrayList<BTCSignHashDTO> list = new ArrayList<>();
        for (UTXO utxo : utxos) {
            TransactionOutPoint
                    outPoint = new TransactionOutPoint(networkParameters, utxo.getIndex(), utxo.getHash());
            BTCSignHashDTO rustSign = new BTCSignHashDTO();
            byte[] singHash = btcTransaction.getSingHash(outPoint, utxo.getScript(), BTCTransaction.SigHash.ALL, true);
            rustSign.setSignHash(HexUtil.encodeHexStr(singHash));
            rustSign.setIndex(utxo.getIndex());
            rustSign.setUtxoHash(utxo.getHash().toString());
            list.add(rustSign);
        }
        SignTransactionVO params = new SignTransactionVO();
        params.setUn_sign_hash_vec(list);
        return params;
    }

    @Override
    public BigInteger getFee(Long gasPrice, String from, String amountString) {
        List<BitcoindRpcClient.Unspent> unspents = getBitcoinClient().listUnspent(1, 9999999, from);
        //输入交易
        int inputs = 0;
        //输出交易
        int outputs = 2;
        BigDecimal sum = new BigDecimal(0);
        // 手续费
        BigInteger gasPriceInteger = new BigDecimal(gasPrice).toBigInteger();
        BigInteger fee = BigInteger.ZERO;
        //转账金额 重置资产时为null
        BigInteger amount = Optional.ofNullable(amountString)
                .map(v -> new BigDecimal(v).multiply(new BigDecimal("100000000")).toBigInteger())
                .orElse(null);
        BigInteger newBlance = BigInteger.valueOf(0);
        for (BitcoindRpcClient.Unspent v : unspents) {
            inputs++;
            //判断utxo 是否够用了
            sum = sum.add(v.amount());
            BigInteger sumAmount = sum.multiply(new BigDecimal("100000000")).toBigInteger();
            // BitCoin计算手续费  = 字节数 * 费率(sat / byte)
            long byteSize = inputs * 128L + outputs * 34L;
            fee = gasPriceInteger.multiply(new BigDecimal(byteSize).toBigInteger());
            if (Objects.isNull(amount)) {
                //重置资产
                continue;
            }
            newBlance = sumAmount.subtract(amount).subtract(fee);
            if ((newBlance.compareTo(BigInteger.ZERO) >= 0)) {
                break;
            }
        }
        //判断是否余额不足
        if (!(newBlance.compareTo(BigInteger.ZERO) >= 0)) {
            throw new BizException(ResultCode.CLIENT_RESOURCE_ACCOUNT_BALANCE_OVER);
        }
        return fee;
    }
}