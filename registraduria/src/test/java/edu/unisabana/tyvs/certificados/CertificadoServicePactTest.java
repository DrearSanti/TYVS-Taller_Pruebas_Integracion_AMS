package edu.unisabana.tyvs.certificados;

import au.com.dius.pact.consumer.MockServer;
import au.com.dius.pact.consumer.dsl.PactDslWithProvider;
import au.com.dius.pact.consumer.junit5.PactConsumerTestExt;
import au.com.dius.pact.consumer.junit5.PactTestFor;
import au.com.dius.pact.core.model.PactSpecVersion;
import au.com.dius.pact.core.model.RequestResponsePact;
import au.com.dius.pact.core.model.annotations.Pact;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * LADO CONSUMIDOR del contract testing.
 *
 * El problema que resuelve: en el modulo de mocks aprendimos a simular al
 * colaborador para que la prueba sea rapida. Pero un mock siempre responde lo
 * que nosotros le dijimos que respondiera. Si el proveedor real cambia su
 * contrato, el mock sigue verde y el fallo aparece en produccion.
 *
 * La prueba de sistema (RegistryControllerIT) tampoco lo cubre: verifica al
 * proveedor contra si mismo, sin saber que espera el consumidor.
 *
 * Pact cierra ese hueco. Aqui el CONSUMIDOR declara que espera, la prueba corre
 * contra un servidor simulado que se comporta segun esa declaracion, y como
 * subproducto se genera un archivo de PACTO en target/pacts/. Luego el
 * proveedor verifica, en su propio pipeline, que sigue cumpliendolo.
 *
 * Nunca hace falta levantar los dos servicios a la vez.
 */
@ExtendWith(PactConsumerTestExt.class)
@PactTestFor(providerName = "registraduria", port = "0", pactVersion = PactSpecVersion.V3)
@DisplayName("Contrato: certificados -> registraduria")
class CertificadoServicePactTest {

    private static final Map<String, String> JSON =
            Map.of("Content-Type", "application/json");

    /**
     * Interaccion 1: una persona valida se registra y responde VALID.
     *
     * Note que el pacto describe la FORMA del intercambio, no la logica del
     * proveedor. Al consumidor no le importa por que la Registraduria considera
     * valida a esta persona; le importa recibir 200 y el texto "VALID".
     */
    @Pact(consumer = "certificados", provider = "registraduria")
    public RequestResponsePact votanteValido(PactDslWithProvider builder) {
        return builder
                .given("no hay ningun votante registrado con id 900")
                .uponReceiving("un registro de votante valido")
                .path("/register")
                .method("POST")
                .headers(JSON)
                .body("{\"name\":\"Ana\",\"id\":900,\"age\":30,\"gender\":\"FEMALE\",\"alive\":true}")
                .willRespondWith()
                .status(200)
                .body("VALID")
                .toPact();
    }

    @Test
    @PactTestFor(pactMethod = "votanteValido")
    @DisplayName("Emite certificado cuando la Registraduria responde VALID")
    void emiteCertificadoCuandoElVotanteEsValido(MockServer mockServer) {
        // Arrange: el cliente apunta al servidor simulado por Pact
        CertificadoService servicio =
                new CertificadoService(new RegistraduriaClient(mockServer.getUrl()));

        // Act
        String certificado = servicio.emitirCertificado(900, "Ana", 30, "FEMALE", true);

        // Assert
        assertEquals("CERT-900", certificado);
    }

    /**
     * Interaccion 2: un votante ya registrado responde DUPLICATED.
     *
     * El "given" describe el ESTADO del proveedor necesario para que la
     * respuesta sea la esperada. El proveedor tendra que saber montar ese
     * estado cuando verifique el pacto.
     */
    @Pact(consumer = "certificados", provider = "registraduria")
    public RequestResponsePact votanteDuplicado(PactDslWithProvider builder) {
        return builder
                .given("ya existe un votante registrado con id 901")
                .uponReceiving("un registro de votante repetido")
                .path("/register")
                .method("POST")
                .headers(JSON)
                .body("{\"name\":\"Luis\",\"id\":901,\"age\":40,\"gender\":\"MALE\",\"alive\":true}")
                .willRespondWith()
                .status(200)
                .body("DUPLICATED")
                .toPact();
    }

    @Test
    @PactTestFor(pactMethod = "votanteDuplicado")
    @DisplayName("No emite certificado cuando la Registraduria responde DUPLICATED")
    void noEmiteCertificadoCuandoElVotanteEstaDuplicado(MockServer mockServer) {
        // Arrange
        CertificadoService servicio =
                new CertificadoService(new RegistraduriaClient(mockServer.getUrl()));

        // Act
        String certificado = servicio.emitirCertificado(901, "Luis", 40, "MALE", true);

        // Assert
        assertNull(certificado);
    }
        /**
     * Interaccion 3: un votante menor de edad no recibe certificado.
     *
     * A diferencia de las dos interacciones anteriores, este "given" no
     * depende de datos previamente guardados: la edad viene en el propio
     * cuerpo de la peticion. El proveedor no necesita montar nada en la BD,
     * pero igual declara el @State para que Pact sepa que la interaccion
     * existe y quede documentada.
     */
    @Pact(consumer = "certificados", provider = "registraduria")
    public RequestResponsePact votanteMenorDeEdad(PactDslWithProvider builder) {
        return builder
                .given("un votante menor de edad con id 902")
                .uponReceiving("un registro de votante menor de edad")
                .path("/register")
                .method("POST")
                .headers(JSON)
                .body("{\"name\":\"Sofia\",\"id\":902,\"age\":10,\"gender\":\"FEMALE\",\"alive\":true}")
                .willRespondWith()
                .status(200)
                .body("UNDERAGE")
                .toPact();
    }

    @Test
    @PactTestFor(pactMethod = "votanteMenorDeEdad")
    @DisplayName("No emite certificado cuando la Registraduria responde UNDERAGE")
    void noEmiteCertificadoCuandoElVotanteEsMenorDeEdad(MockServer mockServer) {
        // Arrange
        CertificadoService servicio =
                new CertificadoService(new RegistraduriaClient(mockServer.getUrl()));

        // Act
        String certificado = servicio.emitirCertificado(902, "Sofia", 10, "FEMALE", true);

        // Assert
        assertNull(certificado);
    }
}
