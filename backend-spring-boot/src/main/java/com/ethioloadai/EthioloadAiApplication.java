package com.ethioloadai;

import com.ethioloadai.e2e.E2eDatasourceGuardInitializer;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class EthioloadAiApplication {

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(EthioloadAiApplication.class);
        application.addInitializers(new E2eDatasourceGuardInitializer());
        application.run(args);
    }
}
