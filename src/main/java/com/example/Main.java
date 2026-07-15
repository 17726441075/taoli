package com.example;

import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.EnumMap;
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
    coin,    
    longExchange,shortExchange,   
    openCha,closeCha,
    longCha,shortCha,
    allFee,
    longFee,shortFee,
    longRate,ShortRate,
    longMaxFee,shortMaxFee,
    longIndexCha,shortIndexCha,
    longTurnover,shortTurnover,
    longLast,shortLast,
    longLot,shortLot,
    longMinSz,shortMinSz,
    longMutil,shortMutil,
    longIndex,shortIndex,
    longMark,shortMark,
    longAskPce,shortAskPce,
    longBidPce,shortBidPce,
    longAskSz,shortAskSz,
    longBidSz,shortBidSz
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
    public HttpClient httpClient() {
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

    @Override
    public void run(ApplicationArguments args) throws Exception {
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
        dataService.getFutures()
                   .forEach((k,map)->{
                        log.info("{} {}",k,map);
                    });
        return ;
    }
    
}