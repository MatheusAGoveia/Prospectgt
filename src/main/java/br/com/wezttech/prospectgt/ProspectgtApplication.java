package br.com.wezttech.prospectgt;

import java.time.Clock;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ProspectgtApplication {

  public static void main(String[] args) {
    SpringApplication.run(ProspectgtApplication.class, args);
  }

  @Bean
  Clock clock() {
    return Clock.systemUTC();
  }

  @Bean
  org.springframework.boot.web.server.WebServerFactoryCustomizer<org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory> socketProtocol() {
    // NIO2 avoids the selector pipe that fails on Windows hosts with restricted AF_UNIX.
    return factory -> factory.setProtocol("org.apache.coyote.http11.Http11Nio2Protocol");
  }
}
