package edu.unisabana.tyvs.registry.delivery.rest;

import au.com.dius.pact.provider.junit5.HttpTestTarget;
import au.com.dius.pact.provider.junit5.PactVerificationContext;
import au.com.dius.pact.provider.junit5.PactVerificationInvocationContextProvider;
import au.com.dius.pact.provider.junitsupport.Provider;
import au.com.dius.pact.provider.junitsupport.State;
import au.com.dius.pact.provider.junitsupport.loader.PactFolder;

import edu.unisabana.tyvs.registry.application.port.out.RegistryRepositoryPort;
import edu.unisabana.tyvs.registry.application.usecase.Registry;
import edu.unisabana.tyvs.registry.domain.model.Gender;
import edu.unisabana.tyvs.registry.domain.model.Person;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.TestPropertySource;

/**
 * LADO PROVEEDOR del contract testing.
 *
 * Levanta la Registraduria completa y reproduce, una por una, TODAS las
 * interacciones que el consumidor declaro en target/pacts/. Si alguien cambia
 * el endpoint, el formato del cuerpo o el codigo de respuesta, esta prueba
 * falla en el pipeline del PROVEEDOR, antes de romper al consumidor en
 * produccion.
 *
 * Lo valioso: el consumidor nunca se levanta. Solo se lee su pacto.
 *
 * Cada @State corresponde a un "given" del pacto y prepara el estado que esa
 * interaccion necesita. Es responsabilidad del proveedor saber montar ese
 * estado; el consumidor solo lo nombra.
 *
 * En un proyecto real el pacto no se leeria de una carpeta local sino de un
 * Pact Broker, al que el consumidor publica desde su propio pipeline.
 */
@Provider("registraduria")
@PactFolder("target/pacts")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = "registry.jdbc-url=jdbc:h2:mem:regdb_pact;DB_CLOSE_DELAY=-1")
class RegistraduriaProviderPactIT {

    @LocalServerPort
    private int puerto;

    @Autowired
    private RegistryRepositoryPort repo;

    @Autowired
    private Registry registry;

    @BeforeEach
    void apuntarAlServidor(PactVerificationContext context) throws Exception {
        repo.deleteAll();
        if (context != null) {
            context.setTarget(new HttpTestTarget("localhost", puerto));
        }
    }

    @TestTemplate
    @ExtendWith(PactVerificationInvocationContextProvider.class)
    void verificarPacto(PactVerificationContext context) {
        context.verifyInteraction();
    }

    /** Estado para la interaccion "un registro de votante valido". */
    @State("no hay ningun votante registrado con id 900")
    void sinVotante900() throws Exception {
        repo.deleteAll();
    }

    /** Estado para la interaccion "un registro de votante repetido". */
    @State("ya existe un votante registrado con id 901")
    void conVotante901() {
        registry.registerVoter(new Person("Luis", 901, 40, Gender.MALE, true));
    }
        /**
     * Estado para la interaccion "un registro de votante menor de edad".
     *
     * No hay nada que montar en la BD: UNDERAGE lo decide la regla de
     * negocio de Registry a partir de la edad que viene en la peticion, no
     * de un dato previamente guardado. El metodo existe igual porque Pact
     * exige un @State que coincida con cada "given" del pacto.
     */
    @State("un votante menor de edad con id 902")
    void votanteMenorDeEdad902() throws Exception {
        repo.deleteAll();
    }
}
