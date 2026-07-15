package com.example;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.Map;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Service;

import com.alibaba.fastjson2.annotation.JSONField;

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
class Taoli{
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
    @JSONField(serialize = false)
    public static final EnumMap<Exchange,Map<String,Map<Ticker,BigDecimal>>> futures = new EnumMap<>(Exchange.class) ;
}
@EnableScheduling
@SpringBootApplication
public class Main {  
    public static void main(String[] args) { SpringApplication.run(Main.class, args); } 
}

@Configuration
class SchedulerConfig {

    @Bean
    public ThreadPoolTaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(10);
        scheduler.setThreadNamePrefix("TaoLi.");
        scheduler.setAwaitTerminationSeconds(60);
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        return scheduler;
    }

}
@Slf4j
@Service
class Test {

    @Scheduled(fixedRate = 100)
    public void test(){
        log.info("tets");
        return ;
    }
    
}