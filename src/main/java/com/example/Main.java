package com.example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

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

