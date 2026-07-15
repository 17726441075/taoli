package com.example;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.epoll.EpollIoHandler;
import io.netty.channel.epoll.EpollSocketChannel;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketClientProtocolConfig;
import io.netty.handler.codec.http.websocketx.WebSocketClientProtocolHandler;
import io.netty.handler.codec.http.websocketx.WebSocketVersion;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.ReferenceCountUtil;
import jakarta.annotation.Resource;
import lombok.Builder;
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
        scheduler.setWaitForTasksToCompleteOnShutdown(false); 
        scheduler.setAwaitTerminationSeconds(0);           
        scheduler.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardPolicy());
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

    @Scheduled(fixedRate = 40*1000)
    public void fund() throws Exception{
        String json = client.send(
                        HttpRequest.newBuilder()
                                    .uri(URI.create("https://api.bitget.com/api/v3/market/current-fund-rate?category=USDT-FUTURES"))
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
            map.put(Ticker.rateFee, x.getBigDecimal("fundingRateInterval")) ;
            map.put(Ticker.maxFee, x.getBigDecimal("maxFundingRate").multiply(BigDecimal.valueOf(100))) ;
        }              
    }
    
}
@Order(5)
@Slf4j
@Service
class GateService implements ApplicationRunner {
    private static final EventLoopGroup io = new MultiThreadIoEventLoopGroup(1,  EpollIoHandler.newFactory());
    private static final Exchange exchange = Exchange.gate ;

    @Resource
    private HttpClient client ;

    @Resource
    private ThreadPoolTaskScheduler taskScheduler ;

    private Map<String,Map<Ticker,BigDecimal>> tickerMap ;
    
    @Override
    public void run(ApplicationArguments args) throws Exception {
        this.tickerMap = DataService.futures.get(exchange) ;
        connectToGate() ;
    }

    private final void connectToGate() throws Exception {
        URI uri = URI.create("wss://fx-ws.gateio.ws/v4/ws/usdt") ;
        String inetHost = uri.getHost() ;
        int port = uri.getPort() <= 0 ? ("wss".equals(uri.getScheme()) ? 443 : 80) : uri.getPort() ;
        SslContext ssl = SslContextBuilder.forClient().build();
        new Bootstrap()
            .group(io) 
            .channel(EpollSocketChannel.class) 
            .remoteAddress(inetHost, port) 
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 15 * 1000) 
            .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                                if("wss".equals(uri.getScheme()))
                                    ch.pipeline().addLast(ssl.newHandler(ch.alloc(),inetHost ,port)) ;
                                ch.pipeline()
                                    .addLast(new HttpClientCodec())
                                    .addLast(new HttpObjectAggregator(65536))
                                    .addLast(new WebSocketClientProtocolHandler( 
                                            WebSocketClientProtocolConfig.newBuilder()
                                                                        .webSocketUri(uri)                                
                                                                        .version(WebSocketVersion.V13)
                                                                        .maxFramePayloadLength(1024*1024*10)                        
                                                                        .handshakeTimeoutMillis(15 * 1000) 
                                                                        .build()
                                    ))
                                    .addLast(new IdleStateHandler(10, 5, 0))
                                    .addLast(new ChannelDuplexHandler() {
                                                @Override
                                                public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
                                                    if (evt instanceof IdleStateEvent e ) {
                                                        if (e.state() == IdleState.WRITER_IDLE)
                                                            ctx.writeAndFlush(new TextWebSocketFrame(String.format(
                                                                """
                                                                    {
                                                                    "time": %s,
                                                                    "channel":"futures.ping"
                                                                    }
                                                                """, System.currentTimeMillis()/1000) )) ;
                                                        if(e.state() == IdleState.READER_IDLE){
                                                            log.error("{}readTimeOut",exchange);
                                                            ctx.close() ;
                                                        }
                                                    }
                                                    if (evt == WebSocketClientProtocolHandler.ClientHandshakeStateEvent.HANDSHAKE_COMPLETE){
                                                        List<List<String>> buketList = List.of(new LinkedList<>(),new LinkedList<>(),new LinkedList<>(),new LinkedList<>(),new LinkedList<>(),new LinkedList<>(),new LinkedList<>(),new LinkedList<>());
                                                        int ind = 0 ;
                                                        for(String x:tickerMap.keySet()){
                                                            buketList.get(ind).add(x+"_USDT") ;
                                                            ind = ++ind%buketList.size() ;
                                                        }
                                                        for(List<String> payload : buketList){
                                                            String subStr2 = JSON.toJSONString(Map.of("time",System.currentTimeMillis()/1000,"channel","futures.book_ticker","event","subscribe","payload",payload)) ;
                                                            ctx.writeAndFlush(new TextWebSocketFrame(subStr2)) ;
                                                            log.info("bytes: {}",subStr2.getBytes(StandardCharsets.UTF_8).length);
                                                        }
                                                    }
                                                }
                                                @Override
                                                public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                                                    if(msg instanceof TextWebSocketFrame twsf){
                                                        String text = twsf.text() ;
                                                        taskScheduler.execute(()->{
                                                            JSONObject jobj = JSON.parseObject(text) ;
                                                            String channel = jobj.getString("channel") ;
                                                            if(channel.startsWith("futures.pong") || !jobj.containsKey("event") || !"update".equals(jobj.getString("event"))) {
                                                                log.info("{} {}",exchange,text);
                                                                return ;
                                                            }
                                                            JSONObject result = jobj.getJSONObject("result") ;
                                                            String baseCoin = Util.exchangeCoinToBase(exchange, result.getString("s")) ;
                                                            Map<Ticker,BigDecimal> map = tickerMap.get(baseCoin) ;
                                                            map.put(Ticker.askPce, result.getBigDecimal("a")) ;
                                                            map.put(Ticker.askSz, result.getBigDecimal("A")) ;
                                                            map.put(Ticker.bidPce, result.getBigDecimal("b")) ;
                                                            map.put(Ticker.bidSz, result.getBigDecimal("B")) ;
                                                        });
                                                    }
                                                    ReferenceCountUtil.release(msg);
                                                }
                                                @Override
                                                public void channelInactive(ChannelHandlerContext ctx) throws Exception {
                                                    Thread.sleep(1000) ;
                                                    connectToGate();
                                                }
                                    });
                        }         
                    }
            )
            .connect()
            .sync() ;
    }

    @Scheduled(fixedRate = 3000)
    public void contracts() throws Exception{
        String json = client.send(
                        HttpRequest.newBuilder()
                                    .uri(URI.create("https://api.gateio.ws/api/v4/futures/usdt/contracts"))
                                    .GET()
                                    .header("User-Agent", "Mozilla/5.0")
                                    .build(),
                        HttpResponse.BodyHandlers.ofString()
                     ).body();
        for(JSONObject x : JSONArray.parseArray(json).toJavaList(JSONObject.class) ){
            if( !x.getString("type").equals("direct") || !x.getString("status").equals("trading") ) continue; 
            String baseCoin = Util.exchangeCoinToBase(exchange, x.getString("name")) ;
            if(!tickerMap.containsKey(baseCoin))
                continue ;
            Map<Ticker,BigDecimal> map = tickerMap.get(baseCoin) ;
            map.put(Ticker.lastPcE, x.getBigDecimal("last_price")) ;
            map.put(Ticker.indexPce, x.getBigDecimal("index_price")) ;
            map.put(Ticker.markPce, x.getBigDecimal("mark_price")) ;
            map.put(Ticker.fee, x.getBigDecimal("funding_rate").multiply(BigDecimal.valueOf(100))) ;
            map.put(Ticker.rateFee, BigDecimal.valueOf(x.getLongValue("funding_interval")/3600)) ;
            map.put(Ticker.maxFee, x.getBigDecimal("funding_rate_limit").multiply(BigDecimal.valueOf(100))) ;
        }              
    }
    
    @Scheduled(fixedRate = 3*60*1000)
    public void tickers() throws Exception{
        String json = client.send(
                        HttpRequest.newBuilder()
                                    .uri(URI.create("https://api.gateio.ws/api/v4/futures/usdt/tickers"))
                                    .GET()
                                    .header("User-Agent", "Mozilla/5.0")
                                    .build(),
                        HttpResponse.BodyHandlers.ofString()
                     ).body();
        for(JSONObject x : JSONArray.parseArray(json).toJavaList(JSONObject.class) ){
            String baseCoin = Util.exchangeCoinToBase(exchange, x.getString("contract")) ;
            if(!tickerMap.containsKey(baseCoin)) continue ;
            Map<Ticker,BigDecimal> map = tickerMap.get(baseCoin) ;
            map.put(Ticker.turnover, x.getBigDecimal("volume_24h").multiply(map.get(Ticker.mutil)).multiply(map.get(Ticker.lastPcE))) ;
        }              
    }

}
@Builder
@Data
class Taoli {
    private String coin,longExchange,shortExchange;
    private BigDecimal openCha,closeCha;
    private BigDecimal longCha,shortCha;
    private BigDecimal allFee;
    private BigDecimal longFee,shortFee;
    private BigDecimal longRate,shortRate;
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
@Order(6)
@Slf4j
@Service
class TaoliService {

    @Resource
    private StringRedisTemplate stringRedisTemplate ;

    @Scheduled(fixedRate = 20*1000)
    public void tickers() throws Exception{
        // long st = System.currentTimeMillis() ;
        final List<Taoli> list = new LinkedList<>() ;
        Map<Exchange,Map<String,Map<Ticker,BigDecimal>>> futuresTicker = DataService.futures ;
        for(var a:futuresTicker.entrySet())
            for(var b:futuresTicker.entrySet())
                if(a.getKey()!=b.getKey())
                    for(var enA:a.getValue().entrySet())
                        if(b.getValue().containsKey(enA.getKey())){
                            String coin = enA.getKey() ;
                            Map<Ticker,BigDecimal> longTicker = enA.getValue() , shortTicker = b.getValue().get(coin) ;
                            Taoli x = Taoli.builder()
                                           .coin(coin)
                                           .longFee(longTicker.get(Ticker.fee))
                                           .shortFee(shortTicker.get(Ticker.fee))
                                           .longRate(longTicker.get(Ticker.rateFee))
                                           .shortRate(shortTicker.get(Ticker.rateFee))
                                           .longMaxFee(longTicker.get(Ticker.maxFee))
                                           .shortMaxFee(shortTicker.get(Ticker.maxFee))
                                           .longTurnover(longTicker.get(Ticker.turnover))
                                           .shortTurnover(shortTicker.get(Ticker.turnover))
                                           .longLast(longTicker.get(Ticker.lastPcE))
                                           .shortLast(shortTicker.get(Ticker.lastPcE))
                                           .longLot(longTicker.get(Ticker.lotSz))
                                           .shortLot(shortTicker.get(Ticker.lotSz))
                                           .longMinSz(longTicker.get(Ticker.minSz))
                                           .shortMinSz(shortTicker.get(Ticker.minSz))
                                           .longMutil(longTicker.get(Ticker.mutil))
                                           .shortMutil(shortTicker.get(Ticker.mutil))
                                           .longIndex(longTicker.get(Ticker.indexPce))
                                           .shortIndex(shortTicker.get(Ticker.indexPce))
                                           .longMark(longTicker.get(Ticker.markPce))
                                           .shortMark(shortTicker.get(Ticker.markPce))
                                           .longAskPce(longTicker.get(Ticker.askPce))
                                           .shortAskPce(shortTicker.get(Ticker.askPce))
                                           .longBidPce(longTicker.get(Ticker.bidPce))
                                           .shortBidPce(shortTicker.get(Ticker.bidPce))
                                           .longAskSz(longTicker.get(Ticker.askSz))
                                           .shortAskSz(shortTicker.get(Ticker.askSz))
                                           .longBidSz(longTicker.get(Ticker.bidSz))
                                           .shortBidSz(shortTicker.get(Ticker.bidSz))
                                           .build() ;
                            if(x.getShortBidPce()!=null&&x.getLongAskPce()!=null)               
                                x.setOpenCha( 
                                            x.getShortBidPce().subtract(x.getLongAskPce())
                                                            .multiply(BigDecimal.valueOf(100))
                                                            .divide(x.getShortBidPce().add(x.getLongAskPce().divide(BigDecimal.TWO,7,RoundingMode.DOWN)),7,RoundingMode.DOWN)
                                                            );
                            if(x.getShortAskPce()!=null&&x.getLongBidPce()!=null)                                   
                                x.setCloseCha( 
                                            x.getShortAskPce().subtract(x.getLongBidPce())
                                                            .multiply(BigDecimal.valueOf(100))
                                                            .divide(x.getShortAskPce().add(x.getLongBidPce().divide(BigDecimal.TWO,7,RoundingMode.DOWN)),7,RoundingMode.DOWN)
                                                            );
                            if(x.getLongAskPce()!=null&&x.getLongBidPce()!=null)                                                 
                                x.setLongCha(
                                            x.getLongAskPce().subtract(x.getLongBidPce())
                                                            .multiply(BigDecimal.valueOf(100))
                                                            .divide(x.getLongAskPce().add(x.getLongBidPce().divide(BigDecimal.TWO,7,RoundingMode.DOWN)),7,RoundingMode.DOWN)
                                                            ); 
                            if(x.getShortBidPce()!=null&&x.getShortBidPce()!=null)                                 
                                x.setShortCha(
                                            x.getShortAskPce().subtract(x.getShortBidPce())
                                                            .multiply(BigDecimal.valueOf(100))
                                                            .divide(x.getShortAskPce().add(x.getShortBidPce().divide(BigDecimal.TWO,7,RoundingMode.DOWN)),7,RoundingMode.DOWN)
                                                            );
                            if(x.getShortFee()!=null&&x.getLongFee()!=null)                                
                                x.setAllFee(x.getShortFee().subtract(x.getLongFee()).setScale(3,RoundingMode.DOWN));
                            if(x.getLongMark()!=null&&x.getLongIndex()!=null)
                                x.setLongIndexCha(
                                            x.getLongMark().subtract(x.getLongIndex())
                                                        .divide(x.getLongIndex(),7,RoundingMode.DOWN)
                                                        .multiply(BigDecimal.valueOf(100)).setScale(3,RoundingMode.DOWN)
                                                            );
                            if(x.getShortMark()!=null&&x.getShortIndex()!=null)
                                x.setLongIndexCha(
                                            x.getShortMark().subtract(x.getShortIndex())
                                                        .divide(x.getShortIndex(),7,RoundingMode.DOWN)
                                                        .multiply(BigDecimal.valueOf(100)).setScale(3,RoundingMode.DOWN)
                                                            );                                                          
                            list.add(x) ;   
                        }
        // log.info("{}",System.currentTimeMillis()-st);
        stringRedisTemplate.opsForValue().set("qiqi", JSON.toJSONString(list));                
    }

}
