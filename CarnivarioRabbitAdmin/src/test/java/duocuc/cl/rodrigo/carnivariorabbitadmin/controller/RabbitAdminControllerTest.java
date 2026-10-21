package duocuc.cl.rodrigo.carnivariorabbitadmin.controller;

import java.util.stream.Stream;

import duocuc.cl.rodrigo.carnivariorabbitadmin.dto.BindingRequest;
import duocuc.cl.rodrigo.carnivariorabbitadmin.dto.ExchangeRequest;
import duocuc.cl.rodrigo.carnivariorabbitadmin.dto.QueueRequest;
import duocuc.cl.rodrigo.carnivariorabbitadmin.exception.RabbitAdminExceptionHandler;
import duocuc.cl.rodrigo.carnivariorabbitadmin.service.RabbitAdminService;
import duocuc.cl.rodrigo.carnivariorabbitadmin.service.RabbitAdminService.AdminOperationException;
import duocuc.cl.rodrigo.carnivariorabbitadmin.service.RabbitAdminService.Failure;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class RabbitAdminControllerTest {

    private RabbitAdminService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(RabbitAdminService.class);
        mvc = MockMvcBuilders.standaloneSetup(new RabbitAdminController(service))
                .setControllerAdvice(new RabbitAdminExceptionHandler()).build();
    }

    @Test
    void createsQueueAndDelegatesOnlyToService() throws Exception {
        mvc.perform(post("/api/rabbit/queues").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"demo.queue\",\"durable\":true}"))
                .andExpect(status().isCreated()).andExpect(content().string(""));
        verify(service).createQueue(new QueueRequest("demo.queue", true));
        verifyNoMoreInteractions(service);
    }

    @Test
    void deletesQueue() throws Exception {
        mvc.perform(delete("/api/rabbit/queues/demo.queue")).andExpect(status().isNoContent());
        verify(service).deleteQueue("demo.queue");
    }

    @Test
    void createsExchange() throws Exception {
        mvc.perform(post("/api/rabbit/exchanges").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"demo.exchange\",\"type\":\"topic\",\"durable\":true}"))
                .andExpect(status().isCreated());
        verify(service).createExchange(new ExchangeRequest("demo.exchange", "topic", true));
    }

    @Test
    void deletesExchange() throws Exception {
        mvc.perform(delete("/api/rabbit/exchanges/demo.exchange")).andExpect(status().isNoContent());
        verify(service).deleteExchange("demo.exchange");
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void handlesBindingBodiesForPostAndDelete(boolean create) throws Exception {
        var request = create ? post("/api/rabbit/bindings") : delete("/api/rabbit/bindings");
        mvc.perform(request.contentType(MediaType.APPLICATION_JSON)
                .content("{\"exchange\":\"demo.exchange\",\"queue\":\"demo.queue\",\"routingKey\":\"demo.#\"}"))
                .andExpect(status().is(create ? 201 : 204));
        BindingRequest binding = new BindingRequest("demo.exchange", "demo.queue", "demo.#");
        if (create) verify(service).createBinding(binding); else verify(service).deleteBinding(binding);
    }

    @Test
    void getsRealQueueStateProvidedByService() throws Exception {
        when(service.getQueue("demo.queue")).thenReturn(new RabbitAdminService.QueueState("demo.queue", 3, 1));
        mvc.perform(get("/api/rabbit/queues/demo.queue"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("demo.queue"))
                .andExpect(jsonPath("$.messageCount").value(3)).andExpect(jsonPath("$.consumerCount").value(1));
    }

    static Stream<Arguments> invalidBodies() {
        return Stream.of(
                Arguments.of("queues", "{}"),
                Arguments.of("queues", "{\"name\":\" \",\"durable\":true}"),
                Arguments.of("queues", "{\"name\":\"demo.bad/name\",\"durable\":true}"),
                Arguments.of("queues", "{\"name\":\"demo.queue\"}"),
                Arguments.of("queues", "{\"name\":\"demo.queue\",\"durable\":\"invalid\"}"),
                Arguments.of("queues", "{\"name\":\"demo." + "x".repeat(251) + "\",\"durable\":true}"),
                Arguments.of("queues", "{"),
                Arguments.of("exchanges", "{\"name\":\"demo.exchange\",\"type\":\"headers\",\"durable\":true}"),
                Arguments.of("exchanges", "{\"name\":\"demo.exchange\",\"type\":\"\",\"durable\":true}"),
                Arguments.of("exchanges", "{\"name\":\"demo.exchange\",\"type\":\"topic\"}"),
                Arguments.of("bindings", "{\"exchange\":\"demo.exchange\",\"queue\":\"\",\"routingKey\":\"demo.key\"}"),
                Arguments.of("bindings", "{\"exchange\":\"demo.exchange\",\"queue\":\"demo.queue\",\"routingKey\":\"a..b\"}"),
                Arguments.of("bindings", "{\"exchange\":\"demo.exchange\",\"queue\":\"demo.queue\",\"routingKey\":\"a*b\"}"),
                Arguments.of("bindings", "{\"exchange\":\"demo.exchange\",\"queue\":\"demo.queue\",\"routingKey\":\"\"}"));
    }

    @ParameterizedTest
    @MethodSource("invalidBodies")
    void invalidJsonAndDtosReturn400BeforeService(String resource, String body) throws Exception {
        mvc.perform(post("/api/rabbit/" + resource).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("BAD_REQUEST"));
        verifyNoInteractions(service);
    }

    @Test
    void validatesDeleteBindingBodyToo() throws Exception {
        mvc.perform(delete("/api/rabbit/bindings").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @CsvSource({"INVALID,400", "FORBIDDEN,403", "NOT_FOUND,404", "CONFLICT,409", "UNAVAILABLE,503"})
    void translatesServiceFailuresToHttp(Failure failure, int status) throws Exception {
        doThrow(new AdminOperationException(failure)).when(service).deleteQueue(any());
        mvc.perform(delete("/api/rabbit/queues/demo.queue"))
                .andExpect(status().is(status)).andExpect(jsonPath("$.message").isString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"carnivario.eventos", "carnivario.dlx", "notificaciones.confirmacion.queue",
            "notificaciones.confirmacion.dlq"})
    void protectedDeletionReturns403(String name) throws Exception {
        doThrow(new AdminOperationException(Failure.FORBIDDEN)).when(service).deleteQueue(name);
        doThrow(new AdminOperationException(Failure.FORBIDDEN)).when(service).deleteExchange(name);
        mvc.perform(delete("/api/rabbit/queues/" + name)).andExpect(status().isForbidden());
        mvc.perform(delete("/api/rabbit/exchanges/" + name)).andExpect(status().isForbidden());
    }

    @Test
    void protectedBindingReturns403() throws Exception {
        doThrow(new AdminOperationException(Failure.FORBIDDEN)).when(service).createBinding(any());
        mvc.perform(post("/api/rabbit/bindings").contentType(MediaType.APPLICATION_JSON)
                .content("{\"exchange\":\"carnivario.eventos\",\"queue\":\"demo.queue\",\"routingKey\":\"pedido.#\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void unexpectedErrorsDoNotExposeInternalDetails() throws Exception {
        when(service.getQueue("demo.queue")).thenThrow(new IllegalStateException("password=secret; internal.host"));
        mvc.perform(get("/api/rabbit/queues/demo.queue"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().string(not(containsString("secret"))))
                .andExpect(content().string(not(containsString("internal.host"))));
    }

    @Test
    void unsupportedMethodAndMediaTypeHaveCoherentStatuses() throws Exception {
        mvc.perform(put("/api/rabbit/queues")).andExpect(status().isMethodNotAllowed());
        mvc.perform(post("/api/rabbit/queues").contentType(MediaType.TEXT_PLAIN).content("private value"))
                .andExpect(status().isUnsupportedMediaType());
        verifyNoInteractions(service);
    }
}
