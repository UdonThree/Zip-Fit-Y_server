package com.zipfity.zip_fit_y.gpt;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.Map;

@RestController
@RequestMapping("/chatbot")  // 이 컨트롤러는 /chatbot 경로로 시작하는 모든 요청을 처리
public class ChatbotController {

    private final ChatbotService chatbotService;

    @Autowired
    public ChatbotController(ChatbotService chatbotService) {
        this.chatbotService = chatbotService; // 생성자 주입을 통해 ChatbotService를 사용
    }

    @GetMapping("/start")
    public ChatResponse startChat(){
        return chatbotService.startChat();
    }

    // 대화 생성 처리 (POST 요청)
    @PostMapping
    public ChatResponse createChat(@RequestBody ChatRequest request,
                                   @RequestHeader("Authorization") String accessToken,
                                   @RequestHeader("Session-ID") String sessionId) {
        // ChatbotService의 비즈니스 로직을 호출하여 응답을 처리
        return chatbotService.processChat(request, accessToken, sessionId);
    }

    // 대화 조회 처리 (GET 요청)
    @GetMapping
    public ChatResponse getChat(@RequestParam("sessionId") String sessionId,
                                @RequestHeader("Authorization") String accessToken) {
        // 세션 ID로 대화 조회
        return chatbotService.getChatBySessionId(sessionId, accessToken);
    }

    // 주소 확인 처리 (POST 요청)
    @PostMapping("/address")
    public String checkAddress(@RequestBody Map<String, Object> addressInfo,
                               @RequestHeader("Authorization") String accessToken,
                               @RequestHeader("Session-ID") String sessionId) {
        // ChatbotService에서 주소 정보를 처리
        return chatbotService.verifyAddress(addressInfo, accessToken, sessionId);
    }
}
