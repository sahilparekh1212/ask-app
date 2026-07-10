package com.aisandbox.audit.rag.mcp;

import com.aisandbox.audit.rag.RagService;
import com.aisandbox.audit.rag.ScoredChunk;
import com.aisandbox.audit.rag.SourceSummary;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises the MCP wire protocol end-to-end through the servlet layer: JSON-RPC envelopes,
 * version negotiation, notification acknowledgement, tool listing/calls and every error
 * shape a client can trigger.
 */
class McpControllerTest {

	private final RagService ragService = mock(RagService.class);
	private final MockMvc mvc = MockMvcBuilders
		.standaloneSetup(new McpController(ragService, new ObjectMapper()))
		.build();

	private org.springframework.test.web.servlet.ResultActions postRpc(String body) throws Exception {
		return mvc.perform(post("/mcp").contentType(MediaType.APPLICATION_JSON).content(body));
	}

	@Test
	void initializeNegotiatesAKnownProtocolVersion() throws Exception {
		postRpc("""
			{"jsonrpc":"2.0","id":1,"method":"initialize",
			 "params":{"protocolVersion":"2025-03-26","capabilities":{},"clientInfo":{"name":"t","version":"1"}}}
			""")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.jsonrpc").value("2.0"))
			.andExpect(jsonPath("$.id").value(1))
			.andExpect(jsonPath("$.result.protocolVersion").value("2025-03-26"))
			.andExpect(jsonPath("$.result.capabilities.tools").exists())
			.andExpect(jsonPath("$.result.serverInfo.name").value(McpController.SERVER_NAME));
	}

	@Test
	void initializeFallsBackToLatestVersionForUnknownRequests() throws Exception {
		postRpc("""
			{"jsonrpc":"2.0","id":2,"method":"initialize","params":{"protocolVersion":"1999-01-01"}}
			""")
			.andExpect(jsonPath("$.result.protocolVersion").value(McpController.LATEST_PROTOCOL_VERSION));
	}

	@Test
	void notificationsAreAcknowledgedWith202AndNoBody() throws Exception {
		postRpc("""
			{"jsonrpc":"2.0","method":"notifications/initialized"}
			""")
			.andExpect(status().isAccepted());
	}

	@Test
	void pingReturnsAnEmptyResult() throws Exception {
		postRpc("""
			{"jsonrpc":"2.0","id":3,"method":"ping"}
			""")
			.andExpect(jsonPath("$.result").exists())
			.andExpect(jsonPath("$.error").doesNotExist());
	}

	@Test
	void toolsListAdvertisesBothToolsWithSchemas() throws Exception {
		postRpc("""
			{"jsonrpc":"2.0","id":4,"method":"tools/list"}
			""")
			.andExpect(jsonPath("$.result.tools.length()").value(2))
			.andExpect(jsonPath("$.result.tools[0].name").value(McpController.TOOL_SEARCH))
			.andExpect(jsonPath("$.result.tools[0].inputSchema.required[0]").value("query"))
			.andExpect(jsonPath("$.result.tools[1].name").value(McpController.TOOL_SOURCES));
	}

	@Test
	void searchToolReturnsRankedChunksAsTextContent() throws Exception {
		when(ragService.isConfigured()).thenReturn(true);
		when(ragService.search(eq("how does auth work?"), isNull())).thenReturn(List.of(
			new ScoredChunk("docs/adr/0002.md", "Decision", "JWTs are issued by Auth.", 0.91)));

		postRpc("""
			{"jsonrpc":"2.0","id":5,"method":"tools/call",
			 "params":{"name":"search_knowledge","arguments":{"query":"how does auth work?"}}}
			""")
			.andExpect(jsonPath("$.result.isError").value(false))
			.andExpect(jsonPath("$.result.content[0].type").value("text"))
			.andExpect(jsonPath("$.result.content[0].text").value(
				org.hamcrest.Matchers.containsString("docs/adr/0002.md")))
			.andExpect(jsonPath("$.result.content[0].text").value(
				org.hamcrest.Matchers.containsString("JWTs are issued by Auth.")));
	}

	@Test
	void searchToolPassesTopKThrough() throws Exception {
		when(ragService.isConfigured()).thenReturn(true);
		when(ragService.search(eq("q"), eq(3))).thenReturn(List.of());

		postRpc("""
			{"jsonrpc":"2.0","id":6,"method":"tools/call",
			 "params":{"name":"search_knowledge","arguments":{"query":"q","top_k":3}}}
			""")
			.andExpect(jsonPath("$.result.isError").value(false))
			.andExpect(jsonPath("$.result.content[0].text").value(
				org.hamcrest.Matchers.containsString("No matching documentation")));
	}

	@Test
	void searchToolReportsNotConfiguredAsAToolError() throws Exception {
		when(ragService.isConfigured()).thenReturn(false);

		postRpc("""
			{"jsonrpc":"2.0","id":7,"method":"tools/call",
			 "params":{"name":"search_knowledge","arguments":{"query":"q"}}}
			""")
			.andExpect(jsonPath("$.result.isError").value(true))
			.andExpect(jsonPath("$.result.content[0].text").value(
				org.hamcrest.Matchers.containsString("VOYAGE_API_KEY")));
	}

	@Test
	void blankQueryIsAnInvalidParamsError() throws Exception {
		when(ragService.isConfigured()).thenReturn(true);

		postRpc("""
			{"jsonrpc":"2.0","id":8,"method":"tools/call",
			 "params":{"name":"search_knowledge","arguments":{"query":"  "}}}
			""")
			.andExpect(jsonPath("$.error.code").value(-32602))
			.andExpect(jsonPath("$.error.message").value(
				org.hamcrest.Matchers.containsString("query")));
	}

	@Test
	void toolExecutionFailureIsReturnedInsideTheResultWithIsError() throws Exception {
		when(ragService.isConfigured()).thenReturn(true);
		when(ragService.search(eq("q"), isNull())).thenThrow(new IllegalStateException("provider down"));

		postRpc("""
			{"jsonrpc":"2.0","id":9,"method":"tools/call",
			 "params":{"name":"search_knowledge","arguments":{"query":"q"}}}
			""")
			.andExpect(jsonPath("$.error").doesNotExist())
			.andExpect(jsonPath("$.result.isError").value(true))
			.andExpect(jsonPath("$.result.content[0].text").value(
				org.hamcrest.Matchers.containsString("provider down")));
	}

	@Test
	void listSourcesRendersTheIndexInventory() throws Exception {
		when(ragService.isConfigured()).thenReturn(true);
		when(ragService.sources()).thenReturn(List.of(
			new SourceSummary("README.md", 12), new SourceSummary("docs/adr/0001.md", 1)));
		when(ragService.chunkCount()).thenReturn(13L);

		postRpc("""
			{"jsonrpc":"2.0","id":10,"method":"tools/call","params":{"name":"list_sources"}}
			""")
			.andExpect(jsonPath("$.result.isError").value(false))
			.andExpect(jsonPath("$.result.content[0].text").value(
				org.hamcrest.Matchers.containsString("README.md (12 chunks)")))
			.andExpect(jsonPath("$.result.content[0].text").value(
				org.hamcrest.Matchers.containsString("docs/adr/0001.md (1 chunk)")));
	}

	@Test
	void listSourcesOnAnEmptyUnconfiguredIndexSaysSo() throws Exception {
		when(ragService.isConfigured()).thenReturn(false);
		when(ragService.sources()).thenReturn(List.of());

		postRpc("""
			{"jsonrpc":"2.0","id":11,"method":"tools/call","params":{"name":"list_sources"}}
			""")
			.andExpect(jsonPath("$.result.content[0].text").value(
				org.hamcrest.Matchers.containsString("not configured")));
	}

	@Test
	void unknownToolIsAProtocolLevelError() throws Exception {
		postRpc("""
			{"jsonrpc":"2.0","id":12,"method":"tools/call","params":{"name":"nope","arguments":{}}}
			""")
			.andExpect(jsonPath("$.error.code").value(-32602))
			.andExpect(jsonPath("$.error.message").value(
				org.hamcrest.Matchers.containsString("nope")));
	}

	@Test
	void unknownMethodIsMethodNotFound() throws Exception {
		postRpc("""
			{"jsonrpc":"2.0","id":13,"method":"resources/list"}
			""")
			.andExpect(jsonPath("$.error.code").value(-32601));
	}

	@Test
	void malformedJsonIsAParseError() throws Exception {
		postRpc("{not json")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.error.code").value(-32700));
	}

	@Test
	void batchRequestsAreRejected() throws Exception {
		postRpc("""
			[{"jsonrpc":"2.0","id":1,"method":"ping"}]
			""")
			.andExpect(jsonPath("$.error.code").value(-32600));
	}

	@Test
	void getIsMethodNotAllowedBecauseThereIsNoServerStream() throws Exception {
		mvc.perform(get("/mcp")).andExpect(status().isMethodNotAllowed());
	}

}
