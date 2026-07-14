package com.example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;
/***
 * 
 * ssh -i D:\pzl\pzl.pem root@47.91.31.90
 * 
 * scp -i D:\pzl\pzl.pem root@47.91.31.90:~/taoli.log  .\ 
 * 
 * scp -i D:\pzl\pzl.pem .\target\taoli-1.0.jar root@47.91.31.90:~
 * **/


@EnableScheduling
@SpringBootApplication
public class Main {
    public static void main(String[] args) { SpringApplication.run(Main.class, args); }
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