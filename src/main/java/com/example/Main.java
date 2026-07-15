package com.example;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Service;

import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.annotation.JSONField;

import jakarta.annotation.Resource;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
/**
 * Hello world!
 *  
 * ssh -i D:\pzl\pzl.pem root@8.211.150.14
 * 
 * scp -i D:\pzl\pzl.pem .\target\cook-1.0.jar root@8.211.150.14:~
 * 
 * scp -i D:\pzl\pzl.pem root@8.211.150.14:~/cook.log  .\
 */

enum Exchange {
    okx,    
    binance,   
    bybit,
    bitget,
    gate,
    hyper  
}
enum Ticker {
    askPce,
    bidPce,
    askSz,
    bidSz,
    fee,
    RateFee,
    maxFee,
    lastPcE,
    turnover,
    indexPce,
    markPce,
    lotSz,
    minSz,
    mutil,
}
class Util {
    
    public static final String exchangeCoinToBase(Exchange exchange,String coin){
        return switch (exchange) {
            case okx -> coin.substring(0,coin.length()-10) ;
            case binance -> coin.substring(0,coin.length()-4) ;
            case bybit -> coin.substring(0,coin.length()-4) ;
            case bitget -> coin.substring(0,coin.length()-4) ;
            case gate -> coin.substring(0,coin.length()-5) ;
            case hyper -> coin.substring(0,coin.length()-4) ;
            default -> null ;
        };
    }

}
@Data
class Taoli {
    private String coin,longExchange,shortExchange;
    private BigDecimal openCha,closeCha;
    private BigDecimal longCha,shortCha;
    private BigDecimal allFee;
    private BigDecimal longFee,shortFee;
    private BigDecimal longRate,ShortRate;
    private BigDecimal longMaxFee,shortMaxFee;
    private BigDecimal longIndexCha,shortIndexCha;
    private BigDecimal longTurnover,shortTurnover;
    private BigDecimal longLast,shortLast;
    private BigDecimal longLot,shortLot;
    private BigDecimal longMinSz,shortMinSz;
    private BigDecimal longMutil,shortMutil;
    private BigDecimal longIndex,shortIndex;
    private BigDecimal longMark,shortMark;
    private BigDecimal longAskPce,shortAskPce;
    private BigDecimal longBidPce,shortBidPce;
    private BigDecimal longAskSz,shortAskSz;
    private BigDecimal longBidSz,shortBidSz;
}
@EnableScheduling
@SpringBootApplication
public class Main {  
    public static void main(String[] args) { SpringApplication.run(Main.class, args); } 
}
@Order(-1)
@Configuration
class AllConfig {

    @Bean
    public ThreadPoolTaskScheduler schedulers() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(16);
        scheduler.setThreadNamePrefix("TaoLi.");
        return scheduler;
    }

    @Bean
    public HttpClient client() {
        return HttpClient.newBuilder()
                .executor(Executors.newVirtualThreadPerTaskExecutor())
                .connectTimeout(Duration.ofSeconds(5))  
                .followRedirects(HttpClient.Redirect.NORMAL) 
                .build();
    }

}
@Order(0)
@Slf4j
@Data
@Service
class DataService implements ApplicationRunner{
    @JSONField(serialize = false)
    private final EnumMap<Exchange,Map<String,Map<Ticker,BigDecimal>>> futures = new EnumMap<>(Exchange.class) ;

    @Resource
    private HttpClient client ;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        for(var x:Exchange.values())
            futures.put(x, new HashMap<>()) ;
        log.info(futures.toString());

        String json = client.send(
                            HttpRequest.newBuilder()
                                       .uri(URI.create("https://openapi.okx.com/api/v5/public/instruments?instType=SWAP"))
                                       .GET()
                                       .header("User-Agent", "Mozilla/5.0")
                                       .build(),
                            HttpResponse.BodyHandlers.ofString()
                      ).body(); 
        for(JSONObject x : JSONObject.parseObject(json).getJSONArray("data").toJavaList(JSONObject.class)){
            String instId =  x.getString("instId")  ;
            if(   !x.getString("ctType").equals("linear") 
                    || !x.getString("settleCcy").equals("USDT")
                    || !x.getString("ruleType").equals("normal") 
                    || !x.getString("state").equals("live") )
                        continue;
            if(instId.equals("BB-USDT-SWAP")||
                instId.equals("OPENAI-USDT-SWAP")||
                instId.equals("ANTHROPIC-USDT-SWAP")) 
                continue ;
            Exchange exchange = Exchange.okx ;
            Map<Ticker,BigDecimal> map = new EnumMap<>(Ticker.class) ;
            for(var ticker:Ticker.values())
                map.put(ticker, null) ;
            futures.get(exchange).put(Util.exchangeCoinToBase(exchange, instId), map) ; 
            map.put(Ticker.lotSz,  x.getBigDecimal("lotSz").multiply(x.getBigDecimal("ctVal"))) ;
            map.put(Ticker.minSz,  x.getBigDecimal("minSz").multiply(x.getBigDecimal("ctVal"))) ;
            map.put(Ticker.mutil,  x.getBigDecimal("ctVal")) ;
        }
        json = client.send(
                            HttpRequest.newBuilder()
                                       .uri(URI.create("https://fapi.binance.com/fapi/v1/exchangeInfo"))
                                       .GET()
                                       .header("User-Agent", "Mozilla/5.0")
                                       .build(),
                            HttpResponse.BodyHandlers.ofString()
                      ).body();
        for(JSONObject x : JSONObject.parseObject(json).getJSONArray("symbols").toJavaList(JSONObject.class)){
            String symbol = x.getString("symbol") ;
            if(  !x.getString("contractType").endsWith("PERPETUAL")
                    || !x.getString("marginAsset").equals("USDT")
                    || !x.getString("status").equals("TRADING") )
                        continue;
            Exchange exchange = Exchange.binance ;
            Map<Ticker,BigDecimal> map = new EnumMap<>(Ticker.class) ;
            for(var ticker:Ticker.values())
                map.put(ticker, null) ;
            futures.get(exchange).put(Util.exchangeCoinToBase(exchange, symbol), map) ;            
            // protyMap[ba].put(symbol, new BigDecimal[]{
            //                                             x.getJSONArray("filters").getJSONObject(2).getBigDecimal("stepSize"),
            //                                             x.getJSONArray("filters").getJSONObject(2).getBigDecimal("minQty"),
            //                                             null}) ;               
        }
        futures.forEach((k,v)->{
            log.info("{} {}",k,v.size());
        });
        log.info(futures.get(Exchange.binance).toString());              
    }

}
@Slf4j
@Service
class Test {

    @Resource
    private DataService dataService ;

    @Scheduled(fixedRate = 2000)
    public void test(){
        log.info("tets");
        return ;
    }
    
}