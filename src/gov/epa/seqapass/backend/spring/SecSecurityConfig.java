package gov.epa.seqapass.backend.spring;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportResource;

@Configuration
@ImportResource({ "classpath:webSecurityConfig.xml" })
@ComponentScan("gov.epa.seqapass.backend.security")
public class SecSecurityConfig {

    public SecSecurityConfig() {
        super();
    }

}