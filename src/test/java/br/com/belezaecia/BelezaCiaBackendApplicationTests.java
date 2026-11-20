package br.com.belezaecia;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "app.agendamentos.auto-concluir.enabled=false")
class BelezaCiaBackendApplicationTests {

	@Test
	void contextLoads() {
	}

}
