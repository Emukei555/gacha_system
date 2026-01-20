//package com.yourcompany.web.controller;
//
//import com.fasterxml.jackson.databind.ObjectMapper;
//import com.yourcompany.domain.shared.exception.GachaErrorCode;
//import com.yourcompany.domain.shared.result.Result;
//import com.yourcompany.schoolasset.application.service.GachaService;
//import com.yourcompany.security.*;
//import com.yourcompany.web.dto.GachaDto;
//import org.junit.jupiter.api.DisplayName;
//import org.junit.jupiter.api.Test;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
//import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
//import org.springframework.boot.test.mock.mockito.MockBean;
//import org.springframework.http.MediaType;
//import org.springframework.security.test.context.support.WithMockUser;
//import org.springframework.test.web.servlet.MockMvc;
//
//import java.util.Collections;
//import java.util.UUID;
//
//import static org.mockito.ArgumentMatchers.any;
//import static org.mockito.ArgumentMatchers.eq;
//import static org.mockito.Mockito.when;
//import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
//import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
//import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
//
//@WebMvcTest(GachaController.class)
//@AutoConfigureMockMvc(addFilters = false) // Securityフィルタをバイパス（純粋なControllerテスト）
//class GachaControllerTest {
//
//    @Autowired
//    private MockMvc mockMvc;
//
//    @Autowired
//    private ObjectMapper objectMapper;
//
//    @MockBean
//    private GachaService gachaService;
//
//    // Security依存のMock (起動に必要)
//    @MockBean
//    private JwtTokenProvider jwtTokenProvider;
//
//    private final UUID poolId = UUID.randomUUID();
//
//    @Test
//    @DisplayName("【異常系】バリデーションエラー: 回数が負の数の場合、400 Bad Request を返す")
//    void shouldReturn400WhenDrawCountIsNegative() throws Exception {
//        // Given
//        // drawCount = -1 (不正な値)
//        GachaDto.DrawRequest invalidRequest = new GachaDto.DrawRequest(poolId, -1);
//
//        // When & Then
//        mockMvc.perform(post("/api/v1/gachas/draw")
//                        .contentType(MediaType.APPLICATION_JSON)
//                        .content(objectMapper.writeValueAsString(invalidRequest)))
//                .andExpect(status().isBadRequest());
//        // .andExpect(jsonPath("$.message").exists()); // GlobalExceptionHandlerの実装に依存
//    }
//
//    @Test
//    @DisplayName("【異常系】ServiceがFailureを返した場合、GlobalExceptionHandler経由でエラーレスポンスを返す")
//    void shouldReturnErrorResponseWhenServiceFails() throws Exception {
//        // Given
//        GachaDto.DrawRequest request = new GachaDto.DrawRequest(poolId, 10);
//
//        // Serviceが残高不足エラー(Result.Failure)を返すようにモック
//        when(gachaService.drawGacha(any(), any()))
//                .thenReturn(Result.failure(GachaErrorCode.INSUFFICIENT_BALANCE));
//
//        // When & Then
//        // Controller実装で Failure -> throw GachaException しているので、
//        // GlobalExceptionHandler が正しく設定されていれば、エラーレスポンスが返るはず
//        mockMvc.perform(post("/api/v1/gachas/draw")
//                        .contentType(MediaType.APPLICATION_JSON)
//                        .content(objectMapper.writeValueAsString(request)))
//                .andExpect(status().isBadRequest()) // INSUFFICIENT_BALANCEは400系想定
//                .andExpect(jsonPath("$.code").value("INSUFFICIENT_BALANCE"));
//    }
//
//    @Test
//    @DisplayName("【異常系】サーバー内部エラーの場合、500 Internal Server Error を返す")
//    void shouldReturn500WhenInternalError() throws Exception {
//        // Given
//        GachaDto.DrawRequest request = new GachaDto.DrawRequest(poolId, 1);
//
//        when(gachaService.drawGacha(any(), any()))
//                .thenReturn(Result.failure(GachaErrorCode.INTERNAL_ERROR));
//
//        // When & Then
//        mockMvc.perform(post("/api/v1/gachas/draw")
//                        .contentType(MediaType.APPLICATION_JSON)
//                        .content(objectMapper.writeValueAsString(request)))
//                .andExpect(status().isInternalServerError())
//                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));
//    }
//
//    @Test
//    @DisplayName("【正常系】Serviceが成功した場合、200 OK と結果を返す")
//    @WithMockUser // UserDetailsを取得するためにダミーユーザーをセット
//    void shouldReturn200WhenSuccess() throws Exception {
//        // Given
//        GachaDto.DrawRequest request = new GachaDto.DrawRequest(poolId, 1);
//        GachaDto.DrawResponse mockResponse = new GachaDto.DrawResponse(
//                "req-123", 300, 0, Collections.emptyList()
//        );
//
//        when(gachaService.drawGacha(any(), eq(request)))
//                .thenReturn(Result.success(mockResponse));
//
//        // When & Then
//        mockMvc.perform(post("/api/v1/gachas/draw")
//                        .contentType(MediaType.APPLICATION_JSON)
//                        .content(objectMapper.writeValueAsString(request)))
//                .andExpect(status().isOk())
//                .andExpect(jsonPath("$.transactionId").value("req-123"));
//    }
//}