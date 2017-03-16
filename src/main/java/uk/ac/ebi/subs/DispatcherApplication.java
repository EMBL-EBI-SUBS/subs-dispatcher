package uk.ac.ebi.subs;


import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.system.ApplicationPidFileWriter;

@SpringBootApplication
public class DispatcherApplication {
    public static void main(String[] args) {
        SpringApplication springApplication = new SpringApplication( DispatcherApplication.class);
        ApplicationPidFileWriter applicationPidFileWriter = new ApplicationPidFileWriter();
        springApplication.addListeners( applicationPidFileWriter );
        springApplication.run(args);
    }
}
