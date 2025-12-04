package com.secufusion.iam;
import com.secufusion.iam.service.InitializerExecutor;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
//import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(properties = "app.initializer.enabled=false")
class IAMServiceApplicationTest {

//    @MockitoBean
//    private InitializerExecutor initializerExecutor;

    @Test void contextLoads() {

    }
}