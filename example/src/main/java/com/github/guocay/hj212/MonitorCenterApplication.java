package com.github.guocay.hj212;

import com.alibaba.druid.spring.boot.autoconfigure.DruidDataSourceAutoConfigure;
import com.github.guocay.hj212.server.TcpServer;
import lombok.extern.slf4j.Slf4j;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Slf4j
@Configuration
@EnableCaching
@EnableScheduling
@MapperScan("com.github.guocay.hj212.server.portal")
@SpringBootApplication(exclude = {DruidDataSourceAutoConfigure.class})
public class MonitorCenterApplication implements CommandLineRunner {

    @Value("${netty.port}")
    private int port;

    @Value("${netty.url}")
    private String url;

    @Autowired
    private TcpServer server;

    public static void main(String[] args) {
        SpringApplication.run(MonitorCenterApplication.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        //服务端管道关闭的监听器并同步阻塞,直到channel关闭,线程才会往下执行,结束进程
        server.start(url,port).channel().closeFuture().syncUninterruptibly();
    }
}
