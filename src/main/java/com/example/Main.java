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

import org.springframework.beans.factory.InitializingBean;
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

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;

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
    rateFee,
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

    public static final String baseToExchange(Exchange exchange,String coin){
        return switch (exchange) {
            case okx -> coin+"-USDT-SWAP" ;
            case binance -> coin+"USDT" ;
            case bybit -> coin+"USDT" ;
            case bitget -> coin+"USDT"  ;
            case gate -> coin+"_USDT";
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
@Service
class DataService implements InitializingBean{
    public static final EnumMap<Exchange,Map<String,Map<Ticker,BigDecimal>>> futures = new EnumMap<>(Exchange.class) ;

    @Resource
    private HttpClient client ;

    @Override
    public void afterPropertiesSet() throws Exception {
        for(var x:Exchange.values())
            futures.put(x, new HashMap<>()) ;
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
                    || !x.getString("status").equals("TRADING") )
                        continue;
            Exchange exchange = Exchange.binance ;
            Map<Ticker,BigDecimal> map = new EnumMap<>(Ticker.class) ;
            for(var ticker:Ticker.values())
                map.put(ticker, null) ;
            futures.get(exchange).put(Util.exchangeCoinToBase(exchange, symbol), map) ;
            map.put(Ticker.lotSz,  x.getJSONArray("filters").getJSONObject(2).getBigDecimal("stepSize")) ;
            map.put(Ticker.minSz,  x.getJSONArray("filters").getJSONObject(2).getBigDecimal("minQty")) ;
            map.put(Ticker.mutil,  null) ;
        }
        json = client.send(
                        HttpRequest.newBuilder()
                                   .uri(URI.create("https://api.bybit.com/v5/market/instruments-info?category=linear&status=Trading"))
                                   .GET()
                                   .header("User-Agent", "Mozilla/5.0")
                                   .build(),
                        HttpResponse.BodyHandlers.ofString()
                ).body(); 
        for(JSONObject x : JSONObject.parseObject(json).getJSONObject("result").getJSONArray("list").toJavaList(JSONObject.class) ){
            String symbol = x.getString("symbol");
            if(  !x.getString("contractType").equals("LinearPerpetual")
                    || !x.getString("status").equals("Trading") )
                    continue ;
            Exchange exchange = Exchange.bybit ;
            Map<Ticker,BigDecimal> map = new EnumMap<>(Ticker.class) ;
            for(var ticker:Ticker.values())
                map.put(ticker, null) ;
            futures.get(exchange).put(Util.exchangeCoinToBase(exchange, symbol), map) ;
            map.put(Ticker.lotSz,   x.getJSONObject("lotSizeFilter").getBigDecimal("qtyStep")) ;
            map.put(Ticker.minSz,  x.getJSONObject("lotSizeFilter").getBigDecimal("minOrderQty")) ;
            map.put(Ticker.mutil,  null) ;        
        }
        json = client.send(
                    HttpRequest.newBuilder()
                                .uri(URI.create("https://api.bitget.com/api/v3/market/instruments?category=USDT-FUTURES"))
                                .GET()
                                .header("User-Agent", "Mozilla/5.0")
                                .build(),
                    HttpResponse.BodyHandlers.ofString()
                  ).body();
        for(JSONObject x : JSONObject.parseObject(json).getJSONArray("data").toJavaList(JSONObject.class)){
            String symbol = x.getString("symbol")  ;
            if(  !x.getString("type").equals("perpetual") 
                    || !x.getString("status").equals("online") ) 
                        continue ;
            if(symbol.equals("CATUSDT")) 
                continue ;
            Exchange exchange = Exchange.bitget ;
            Map<Ticker,BigDecimal> map = new EnumMap<>(Ticker.class) ;
            for(var ticker:Ticker.values())
                map.put(ticker, null) ;
            futures.get(exchange).put(Util.exchangeCoinToBase(exchange, symbol), map) ;
            map.put(Ticker.lotSz,   x.getBigDecimal("quantityMultiplier")) ;
            map.put(Ticker.minSz,  x.getBigDecimal("minOrderQty")) ;
            map.put(Ticker.mutil,  null) ;     
        }
        json = client.send(
                  HttpRequest.newBuilder()
                             .uri(URI.create("https://api.gateio.ws/api/v4/futures/usdt/contracts"))
                             .GET()
                             .header("User-Agent", "Mozilla/5.0")
                             .build(),
                  HttpResponse.BodyHandlers.ofString()
                ).body();
        for(JSONObject x : JSONArray.parseArray(json).toJavaList(JSONObject.class) ){
            String name =  x.getString("name") ;
            if( !x.getString("type").equals("direct")
                || !x.getString("status").equals("trading") )
                    continue; 
            if(name.equals("EDGE_USDT")) 
                continue ;
            Exchange exchange = Exchange.gate ;
            Map<Ticker,BigDecimal> map = new EnumMap<>(Ticker.class) ;
            for(var ticker:Ticker.values())
                map.put(ticker, null) ;
            futures.get(exchange).put(Util.exchangeCoinToBase(exchange, name), map) ;
            map.put(Ticker.lotSz,  BigDecimal.ONE.multiply(x.getBigDecimal("quanto_multiplier"))) ;
            map.put(Ticker.minSz,  BigDecimal.ONE.multiply(x.getBigDecimal("quanto_multiplier"))) ;
            map.put(Ticker.mutil,  x.getBigDecimal("quanto_multiplier")) ;      
        }            
        futures.forEach((k,v)->{
            log.info("{} {}",k,v.size());
        });
    }

}
@Order(1)
@Slf4j
@Service
class OkxService implements ApplicationRunner {
    private static final Exchange exchange = Exchange.okx ;

    @Resource
    private HttpClient client ;

    private Map<String,Map<Ticker,BigDecimal>> tickerMap ;
    
    @Override
    public void run(ApplicationArguments args) throws Exception {
        this.tickerMap = DataService.futures.get(exchange) ;
    }

    @Scheduled(fixedRate = 1000)
    public void Monitor() throws Exception{
        log.info("Monitor");
    }

    @Scheduled(fixedRate = 200)
    public void tickers() throws Exception{
        String json = client.send(
                            HttpRequest.newBuilder()
                                       .uri(URI.create("https://openapi.okx.com/api/v5/market/tickers?instType=SWAP"))
                                       .GET()
                                       .header("User-Agent", "Mozilla/5.0")
                                       .build(),
                            HttpResponse.BodyHandlers.ofString()
                      ).body(); 
        for(JSONObject x : JSONObject.parseObject(json).getJSONArray("data").toJavaList(JSONObject.class)){
            String instId =  x.getString("instId") , baseCoin = Util.exchangeCoinToBase(exchange, instId);
            if( !tickerMap.containsKey(baseCoin) ) 
                continue ;
            Map<Ticker,BigDecimal> map = tickerMap.get(baseCoin) ;
            map.put(Ticker.lastPcE,x.getBigDecimal("last"));
            map.put(Ticker.askPce, x.getBigDecimal("askPx"));
            map.put(Ticker.askSz, x.getBigDecimal("askSz"));
            map.put(Ticker.bidPce, x.getBigDecimal("bidPx"));
            map.put(Ticker.bidSz, x.getBigDecimal("bidSz"));
            map.put(Ticker.turnover, x.getBigDecimal("volCcy24h").multiply(map.get(Ticker.lastPcE)));
        } 
    }

    @Scheduled(fixedRate = 7000)
    public void index() throws Exception{
        String json = client.send(
                            HttpRequest.newBuilder()
                                       .uri(URI.create("https://openapi.okx.com/api/v5/market/index-tickers?quoteCcy=USDT"))
                                       .GET()
                                       .header("User-Agent", "Mozilla/5.0")
                                       .build(),
                            HttpResponse.BodyHandlers.ofString()
                      ).body(); 

        for(JSONObject x : JSONObject.parseObject(json).getJSONArray("data").toJavaList(JSONObject.class)){
            String baseCoin =  x.getString("instId").split("-")[0] ;
            if( !tickerMap.containsKey(baseCoin) ) 
                continue ;
            Map<Ticker,BigDecimal> map = tickerMap.get(baseCoin) ;
            map.put(Ticker.indexPce,x.getBigDecimal("idxPx"));
        } 
    }
    
    @Scheduled(fixedRate = 7000)
    public void mark() throws Exception{
        String json = client.send(
                            HttpRequest.newBuilder()
                                       .uri(URI.create("https://openapi.okx.com/api/v5/public/mark-price?instType=SWAP"))
                                       .GET()
                                       .header("User-Agent", "Mozilla/5.0")
                                       .build(),
                            HttpResponse.BodyHandlers.ofString()
                      ).body(); 

        for(JSONObject x : JSONObject.parseObject(json).getJSONArray("data").toJavaList(JSONObject.class)){
            String instId =  x.getString("instId") , baseCoin = Util.exchangeCoinToBase(exchange, instId);
            if( !tickerMap.containsKey(baseCoin) ) 
                continue ;
            Map<Ticker,BigDecimal> map = tickerMap.get(baseCoin) ;
            map.put(Ticker.markPce,x.getBigDecimal("markPx"));
        } 
    }

    @Scheduled(initialDelay = 7000 , fixedRate = 3*60*1000)
    public void funding() throws Exception{
        for(String baseCoin : tickerMap.keySet())
            try {
                Thread.sleep(250);
                String json = client.send(
                                HttpRequest.newBuilder()
                                        .uri(URI.create("https://openapi.okx.com/api/v5/public/funding-rate?instId="+Util.baseToExchange(exchange, baseCoin)))
                                        .GET()
                                        .header("User-Agent", "Mozilla/5.0")
                                        .build(),
                                HttpResponse.BodyHandlers.ofString()
                        ).body(); 
                JSONObject x = JSONObject.parseObject(json).getJSONArray("data").getJSONObject(0) ;
                Map<Ticker,BigDecimal> map = tickerMap.get(baseCoin) ;
                map.put(Ticker.fee,x.getBigDecimal("fundingRate").multiply(BigDecimal.valueOf(100)));
                map.put(Ticker.maxFee,x.getBigDecimal("maxFundingRate").multiply(BigDecimal.valueOf(100)));
                map.put(Ticker.rateFee,BigDecimal.valueOf((x.getLongValue("nextFundingTime")-x.getLongValue("fundingTime"))/3600000));
            } catch (Exception e) {
                log.error("funding error",e);
            }
    }

}
@Order(2)
@Slf4j
@Service
class BinanceService implements ApplicationRunner {
    private static final Exchange exchange = Exchange.binance ;

    @Resource
    private HttpClient client ;

    private Map<String,Map<Ticker,BigDecimal>> tickerMap ;
    
    @Override
    public void run(ApplicationArguments args) throws Exception {
        this.tickerMap = DataService.futures.get(exchange) ;
    }

    @Scheduled(fixedRate = 200)
    public void bookTicker() throws Exception{
        String json = client.send(
                            HttpRequest.newBuilder()
                                       .uri(URI.create("https://fapi.binance.com/fapi/v1/ticker/bookTicker"))
                                       .GET()
                                       .header("User-Agent", "Mozilla/5.0")
                                       .build(),
                            HttpResponse.BodyHandlers.ofString()
                      ).body();
        for( JSONObject x : JSON.parseArray(json, JSONObject.class)){
            String baseCoin =Util.exchangeCoinToBase(exchange, x.getString("symbol")) ;
            if(!tickerMap.containsKey(baseCoin))
                continue ;
            Map<Ticker,BigDecimal> map = tickerMap.get(baseCoin) ;
            map.put(Ticker.bidPce, x.getBigDecimal("bidPrice")) ;
            map.put(Ticker.bidSz, x.getBigDecimal("bidQty")) ;
            map.put(Ticker.askPce, x.getBigDecimal("askPrice")) ;
            map.put(Ticker.askSz, x.getBigDecimal("askQty")) ;
        }
    }

    @Scheduled(fixedRate = 40*1000)
    public void fundingInfo() throws Exception{
        String json = client.send(
                            HttpRequest.newBuilder()
                                       .uri(URI.create("https://fapi.binance.com/fapi/v1/fundingInfo"))
                                       .GET()
                                       .header("User-Agent", "Mozilla/5.0")
                                       .build(),
                            HttpResponse.BodyHandlers.ofString()
                      ).body(); 
        for( JSONObject x : JSON.parseArray(json, JSONObject.class)){
            String baseCoin =Util.exchangeCoinToBase(exchange, x.getString("symbol")) ;
            if(!tickerMap.containsKey(baseCoin))
                continue ;
            Map<Ticker,BigDecimal> map = tickerMap.get(baseCoin) ;
            map.put(Ticker.maxFee, x.getBigDecimal("adjustedFundingRateCap").multiply(BigDecimal.valueOf(100))) ;
            map.put(Ticker.rateFee, x.getBigDecimal("fundingIntervalHours")) ;
        }
    }
    
    @Scheduled(fixedRate = 7*1000)
    public void premiumIndex() throws Exception{
        HttpResponse<String> response = client.send(
                            HttpRequest.newBuilder()
                                       .uri(URI.create("https://fapi.binance.com/fapi/v1/premiumIndex"))
                                       .GET()
                                       .header("User-Agent", "Mozilla/5.0")
                                       .build(),
                            HttpResponse.BodyHandlers.ofString()
                      ); 
        String json = response.body() ;
        for( JSONObject x : JSON.parseArray(json, JSONObject.class)){
            String baseCoin =Util.exchangeCoinToBase(exchange, x.getString("symbol")) ;
            if(!tickerMap.containsKey(baseCoin))
                continue ;
            Map<Ticker,BigDecimal> map = tickerMap.get(baseCoin) ;
            map.put(Ticker.markPce, x.getBigDecimal("markPrice")) ;
            map.put(Ticker.indexPce, x.getBigDecimal("indexPrice")) ;
            map.put(Ticker.fee, x.getBigDecimal("lastFundingRate").multiply(BigDecimal.valueOf(100))) ;
        }
    }

    @Scheduled(fixedRate = 3*1000)
    public void last() throws Exception{
        HttpResponse<String> response = client.send(
                            HttpRequest.newBuilder()
                                       .uri(URI.create("https://fapi.binance.com/fapi/v1/ticker/24hr"))
                                       .GET()
                                       .header("User-Agent", "Mozilla/5.0")
                                       .build(),
                            HttpResponse.BodyHandlers.ofString()
                      ); 
        String json = response.body() ;
        for( JSONObject x : JSON.parseArray(json, JSONObject.class)){
            String baseCoin =Util.exchangeCoinToBase(exchange, x.getString("symbol")) ;
            if(!tickerMap.containsKey(baseCoin))
                continue ;
            Map<Ticker,BigDecimal> map = tickerMap.get(baseCoin) ;
            map.put(Ticker.turnover, x.getBigDecimal("quoteVolume")) ;
            map.put(Ticker.lastPcE, x.getBigDecimal("lastPrice")) ;
        }
        log.info(response.headers().toString());                   
    }
    
}
@Order(3)
@Slf4j
@Service
class BybitService implements ApplicationRunner {
    private static final Exchange exchange = Exchange.bybit ;

    @Resource
    private HttpClient client ;

    private Map<String,Map<Ticker,BigDecimal>> tickerMap ;
    
    @Override
    public void run(ApplicationArguments args) throws Exception {
        this.tickerMap = DataService.futures.get(exchange) ;
    }

    @Scheduled(fixedRate = 200)
    public void tickers() throws Exception{
        String json = client.send(
                            HttpRequest.newBuilder()
                                    .uri(URI.create("https://api.bybit.com/v5/market/tickers?category=linear"))
                                    .GET()
                                    .header("User-Agent", "Mozilla/5.0")
                                    .build(),
                            HttpResponse.BodyHandlers.ofString()
                      ).body();
        for(JSONObject x : JSON.parseObject(json).getJSONObject("result").getList("list", JSONObject.class)){
            String baseCoin =Util.exchangeCoinToBase(exchange, x.getString("symbol")) ;
            if(!tickerMap.containsKey(baseCoin))
                continue ;
            Map<Ticker,BigDecimal> map = tickerMap.get(baseCoin) ;
            map.put(Ticker.lastPcE, x.getBigDecimal("lastPrice")) ;
            map.put(Ticker.indexPce, x.getBigDecimal("indexPrice")) ;
            map.put(Ticker.markPce, x.getBigDecimal("markPrice")) ;
            map.put(Ticker.turnover, x.getBigDecimal("turnover24h")) ;
            map.put(Ticker.fee, x.getBigDecimal("fundingRate").multiply(BigDecimal.valueOf(100))) ;
            map.put(Ticker.askPce, x.getBigDecimal("ask1Price")) ;
            map.put(Ticker.askSz, x.getBigDecimal("ask1Size")) ;
            map.put(Ticker.bidPce, x.getBigDecimal("bid1Price")) ;
            map.put(Ticker.bidSz, x.getBigDecimal("bid1Size")) ;
            map.put(Ticker.rateFee, x.getBigDecimal("fundingIntervalHour")) ;
            map.put(Ticker.maxFee, x.getBigDecimal("fundingCap").multiply(BigDecimal.valueOf(100))) ;
        }              
    }
    
}
@Order(4)
@Slf4j
@Service
class BitgetService implements ApplicationRunner {
    private static final Exchange exchange = Exchange.bitget ;

    @Resource
    private HttpClient client ;

    private Map<String,Map<Ticker,BigDecimal>> tickerMap ;
    
    @Override
    public void run(ApplicationArguments args) throws Exception {
        this.tickerMap = DataService.futures.get(exchange) ;
    }

    @Scheduled(fixedRate = 200)
    public void tickers() throws Exception{
        String json = client.send(
                        HttpRequest.newBuilder()
                                    .uri(URI.create("https://api.bitget.com/api/v3/market/tickers?category=USDT-FUTURES"))
                                    .GET()
                                    .header("User-Agent", "Mozilla/5.0")
                                    .build(),
                        HttpResponse.BodyHandlers.ofString()
                    ).body();
        for(JSONObject x : JSON.parseObject(json).getList("data", JSONObject.class)){
            String baseCoin =Util.exchangeCoinToBase(exchange, x.getString("symbol")) ;
            if(!tickerMap.containsKey(baseCoin))
                continue ;
            Map<Ticker,BigDecimal> map = tickerMap.get(baseCoin) ;
            map.put(Ticker.lastPcE, x.getBigDecimal("lastPrice")) ;
            map.put(Ticker.indexPce, x.getBigDecimal("indexPrice")) ;
            map.put(Ticker.markPce, x.getBigDecimal("markPrice")) ;
            map.put(Ticker.turnover, x.getBigDecimal("turnover24h")) ;
            map.put(Ticker.fee, x.getBigDecimal("fundingRate").multiply(BigDecimal.valueOf(100))) ;
            map.put(Ticker.askPce, x.getBigDecimal("ask1Price")) ;
            map.put(Ticker.askSz, x.getBigDecimal("ask1Size")) ;
            map.put(Ticker.bidPce, x.getBigDecimal("bid1Price")) ;
            map.put(Ticker.bidSz, x.getBigDecimal("bid1Size")) ;
        }              
    }
    
}