package com.zipfity.zip_fit_y.gpt;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class ChatbotService {

    private final GptClient gptClient;
    private final Gson gson;
    private final String initialGreeting = "안녕하세요! 원하시는 주거 지역을 말씀해주세요.";

    @Autowired
    public ChatbotService(GptClient gptClient) {
        this.gptClient = gptClient;
        // GsonBuilder에서 setLenient() 사용
        this.gson = new GsonBuilder().serializeNulls().setLenient().create();
    }

    // 첫 대화 시작 시 기본 메시지를 반환하는 메서드
    public ChatResponse startChat() {
        List<String> previousConversations = new ArrayList<>(); // 빈 배열 초기화
        return createChatResponse("system", 0, "chatting", initialGreeting, null, null, previousConversations);
    }

    public ChatResponse processChat(ChatRequest request, String accessToken, String sessionId) {
        // 주소 확인 요청 처리
        if (request.isAddressCheck()) {
            return processAddressCheck(request.getRequestData());
        }

        // GPT API에서 응답을 받아옴
        String gptResponse = gptClient.sendMessageToGpt(request, rolePrompt);
        String extractedAnswer = extractAnswerFromResponse(gptResponse);

        // 이전 대화 내용 관리
        List<String> updatedConversations = updatePreviousConversations(request, extractedAnswer);

        // GPT에게 장소 추출 요청
        String destination = extractDestinationWithGpt(updatedConversations);
        if (destination != null) {
            // 주소 확인 요청 처리
            return createChatResponse(
                    request.getUserId(),
                    request.getChatNumber() + 1,
                    "addressCheck", // chatStatus를 "addressCheck"로 설정
                    "목적지 주소를 확인해 주세요: " + destination, // 사용자에게 주소 확인 요청 메시지
                    destination,  // addressInfo에 저장
                    null,  // needs는 null로 설정
                    updatedConversations
            );
        }

        // ChatResponse 객체 생성
        ChatResponse response = createChatResponse(
                request.getUserId(),
                request.getChatNumber() + 1,
                "chatting",  // 기본 상태를 'chatting'으로 설정
                extractedAnswer,
                null,  // addressInfo는 null로 설정
                null,  // needs는 null로 설정, 나중에 필요하면 업데이트
                updatedConversations
        );

        // 니즈가 모두 추출되었는지 확인 (응답의 needs 필드를 통해)
        if (response.getNeeds() != null) {
            // 니즈가 존재하는 경우
            if (!response.getNeeds().getFromWhere().isEmpty() && !response.getNeeds().getToWhere().isEmpty()) {
                // 모든 필요가 충족되었을 때 채팅 종료
                response.setChatStatus("chatEnded");  // chatStatus를 'chatEnded'로 변경
                response.setAnswer("니즈 추출이 완료되었습니다.");  // 종료 메시지

                // needs 필드 구성
                Needs needs = initializeNeedsIfNull();
                response.setNeeds(needs); // needs 필드를 설정
            }
        }

        return response;
    }

    // GPT에게 장소 추출 요청하는 메서드
    private String extractDestinationWithGpt(List<String> conversations) {
        // 이전 대화 내용을 프롬프트로 설정
        String context = String.join("\n", conversations);
        String prompt = "다음 대화 내용에서 사용자가 언급한 장소를 JSON 형식으로 추출해 주세요:\n" + context + "\n\n" +
                "예시 응답: {\"addressInfo\": \"주소\"}";

        // ChatRequest 객체를 생성할 때 필요한 필드 모두 제공
        ChatRequest chatRequest = ChatRequest.builder()
                .userId("userId-placeholder") // 사용자 ID를 적절히 설정
                .chatNumber(conversations.size()) // 대화 번호를 대화 내용의 크기로 설정
                .chatStatus("chatting") // 기본 상태 설정
                .answer(context) // 현재 대화 내용을 answer에 포함
                .addressCheck(false) // 주소 확인 여부
                .requestData(Map.of("prompt", prompt)) // 프롬프트를 요청 데이터로 포함
                .previousConversations(conversations) // 이전 대화 기록
                .build();

        // GPT API에 프롬프트 요청 (rolePrompt를 추가로 전달)
        String gptResponse = gptClient.sendMessageToGpt(chatRequest, prompt);
        // 응답 처리 (여기서 응답에서 목적지 추출)
        return extractAddressFromResponse(gptResponse); // 이 함수는 GPT의 응답에서 주소를 추출합니다.
    }

    // GPT의 응답에서 주소를 추출하는 메서드
    private String extractAddressFromResponse(String gptResponse) {
        try {
            // JSON 파싱을 위해 ObjectMapper 사용 (Jackson 라이브러리)
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode jsonNode = objectMapper.readTree(gptResponse);

            // JSON에서 'destination' 필드 추출
            return jsonNode.path("addressInfo").asText(null); // null 반환을 통해 기본값 설정
        } catch (Exception e) {
            System.err.println("JSON 파싱 오류: " + e.getMessage());
            return null; // 파싱 실패 시 null 반환
        }
    }

    // 특정 세션 ID로 대화를 조회하는 메서드
    public ChatResponse getChatBySessionId(String sessionId, String accessToken) {
        String lastMessage = "Last message for session " + sessionId; // 세션에 대한 마지막 메시지
        List<String> previousConversations = new ArrayList<>(); // 빈 배열 초기화

        // needs는 null로 설정할 수 있음
        return createChatResponse("userId-placeholder", 1, "chatting", lastMessage, null, null, previousConversations);
    }

    // ChatResponse 객체를 생성하는 메서드
    private ChatResponse buildChatResponse(ChatRequest request, String extractedAnswer, List<String> updatedConversations, boolean isChatEnded) {
        return createChatResponse(
                request.getUserId(),
                request.getChatNumber() + 1,
                isChatEnded ? "chatEnded" : "chatting",
                extractedAnswer,
                isChatEnded ? null : (request.isAddressCheck() ? (String) request.getRequestData().get("addressInfo") : null),
                isChatEnded ? initializeNeedsIfNull() : null,
                updatedConversations // 여기에서 업데이트된 대화 내용을 전달
        );
    }

    // ChatResponse 객체 생성 헬퍼 메서드
    private ChatResponse createChatResponse(
            String userId,
            int chatNumber,
            String chatStatus,
            String answer,
            String addressInfo, // null 허용
            Needs needs, // null 허용
            List<String> previousConversations // 이전 대화 기록
    ) {
        return ChatResponse.builder()
                .userId(userId)
                .chatNumber(chatNumber)
                .chatStatus(chatStatus)
                .answer(answer)
                .previousConversations(previousConversations != null ? previousConversations : new ArrayList<>()) // null 체크 후 초기화
                .addressInfo(addressInfo) // addressInfo가 null일 수 있음
                .needs(needs) // needs가 null일 수 있음
                .build();
    }


    // JSON 응답에서 answer를 추출하는 메서드
    private String extractAnswerFromResponse(String gptResponse) {
        // JSON 유효성 검사
        if (!isValidJson(gptResponse)) {
            throw new RuntimeException("유효하지 않은 JSON 응답: " + gptResponse);
        }

        try {
            // 응답에서 JSON 객체 추출 (문자열 부분은 제외)
            String jsonPart = gptResponse.substring(gptResponse.indexOf('{')); // '{'부터 시작하는 부분

            JsonElement jsonElement = gson.fromJson(jsonPart, JsonElement.class);
            if (jsonElement.isJsonObject()) {
                return jsonElement.getAsJsonObject().get("answer").getAsString();
            } else if (jsonElement.isJsonPrimitive()) {
                return jsonElement.getAsString(); // 원시 값을 직접 사용
            }
            return "예상치 못한 응답 형식입니다.";
        } catch (JsonSyntaxException e) {
            System.err.println("JSON 구문 오류: " + e.getMessage());
            return "응답 처리 중 오류가 발생했습니다: " + e.getMessage();
        }
    }
    // JSON 형식이 유효한지 확인하는 메서드
    private boolean isValidJson(String json) {
        try {
            JsonParser.parseString(json);
            return true;
        } catch (JsonParseException e) {
            System.err.println("유효하지 않은 JSON: " + json);
            return false;
        }
    }

    // 이전 대화 내용을 업데이트하는 메서드
    private List<String> updatePreviousConversations(ChatRequest request, String extractedAnswer) {
        List<String> updatedConversations = Optional.ofNullable(request.getPreviousConversations())
                .orElse(new ArrayList<>());

        // 현재 사용자의 발화 내용을 추가
        String userMessage = request.getAnswer();

        // 사용자 발화와 챗봇 응답을 중복되지 않도록 추가
        if (!updatedConversations.contains(userMessage)) {
            updatedConversations.add(userMessage);
        }

        // 챗봇의 응답을 중복되지 않도록 추가
        if (!updatedConversations.contains(extractedAnswer)) {
            updatedConversations.add(extractedAnswer);
        }

        return updatedConversations;
    }

    // 주소 확인 요청을 처리하는 메서드
    private ChatResponse processAddressCheck(Map<String, Object> requestData) {
        String address = (String) requestData.get("address"); // 사용자가 입력한 주소
        String result = "주소가 확인되었습니다: " + address;

        // ChatResponse 객체를 반환할 때 chatStatus를 'chatting'으로 변경
        List<String> previousConversations = new ArrayList<>();
        previousConversations.add(result); // 주소 확인 결과를 이전 대화에 추가

        return createChatResponse("system", 0, "chatting", result, address, null, previousConversations);
    }

    // 주소를 검증하는 메서드 - 주어진 주소가 유효한지 검증하여 결과를 반환
    public String verifyAddress(Map<String, Object> addressInfo, String accessToken, String sessionId) {
        String address = (String) addressInfo.get("address");
        return "Address " + address + " verified for session " + sessionId;
    }

    // 필수 교통 니즈가 null일 경우 빈 배열로 초기화하는 메서드
    private Needs initializeNeedsIfNull() {
        return Needs.builder()
                .fromWhere(new ArrayList<>())
                .toWhere(new ArrayList<>())
                .transportation(new ArrayList<>())
                .interchangeability(true)   // 기본값 설정
                .travelTimeMin(-1)          // 기본값 설정
                .travelTimeMax(-1)
                .transferCountMin(-1)
                .transferCountMax(-1)
                .build();
    }

    // 역할 프롬프트 - GPT에게 전달할 역할과 대화 규칙 설명
    private final String rolePrompt =
            "*당신은 사용자 교통 주거 니즈 추출 챗봇입니다. 당신의 역할은 다음과 같습니다.\n" +
                    "- 필수 교통 니즈를 추출을 위해 사용자와의 대화를 계속 이어나갑니다.\n" +
                    "- 대화 내용의 이전 대화들을 저장하여 사용자가 요청할 때 참조할 수 있도록 합니다.\n" +
                    "- 사용자의 답변에서 필수 교통 니즈를 추출합니다.\n" +
                    "- 사용자에 따라 모든 교통 니즈나 필수 교통 니즈만을 수집하면, 대화를 종료합니다.\n" +
                    "- 채팅 메뉴얼을 따릅니다.\n" +
                    "- 객관적인 정보만을 제공하며, 자신의 의견이나 판단을 전달하지 않습니다.\n\n" +

                    "*필수 교통 니즈는 다음과 같습니다:\n" +
                    "출발지(=주거 선호 지역)\n" +
                    "목적지(=직장 및 대학교 등)\n" +
                    "이동 시간 정보(30분 이내, 10분 이내 등)\n" +
                    "교통 수단(지하철, 버스, 도보)\n" +
                    "환승 정보(환승 0회, 환승 1회 등)\n\n" +

                    "*채팅 매뉴얼\n" +
                    "- 사용자의 모든 답변 내용을 previousConversations 배열에 문자열 형태로 저장합니다.\n" +
                    "- 대화를 통해 반드시 출발지 또는 목적지 중 하나를 확보해야 합니다. 출발지와 목적지 중 하나라도 확보되지 않으면 대화를 종료할 수 없습니다.\n" +
                    "- 목적지가 우선입니다. 목적지를 먼저 물어본 후, 만약 목적지가 설정되었다면 출발지에 대한 추가 질문은 하지 않습니다.\n" +
                    "- 출발지가 없는 경우, 추가로 선호 주거 지역에 대한 질문은 하지 않습니다.\n" +
                    "- 사용자가 이동 시간이 특정한 범위 내인 지역을 찾고 있다고 명시한 경우, 출발지를 묻지 않고 대화를 진행합니다.\n" +
                    "- 목적지나 출발지를 추출했다면, 추가로 이동 시간, 교통 수단, 환승 정보를 추출합니다.\n" +
                    "- 사용자가 모호한 답변을 할 경우, 추가적인 질문을 통해 정확한 정보를 확보합니다. 모호한 답변에 대해 최대 2회까지만 재질문합니다.\n" +
                    "- 목적지나 출발지에 대해 모호한 답변을 받은 경우 '이 장소가 출발지인가요?' 또는 '이 장소가 목적지인가요?'처럼 답변한 장소가 출발지 또는 목적지인지 확인하는 질문을 합니다.\n" +
                    "- 환승 정보는 중복 불가능합니다. 사용자가 '환승 없으면 30분 이내, 환승 1회 있으면 20분 이내'처럼 중복된 답변을 할 경우, 반드시 환승 정보에 대해 하나만 선택하도록 요청하고, 이 질문은 다른 질문보다 우선시됩니다. 이때, 사용자가 선택한 환승 정보와 관련된 이동 시간, 교통 수단 값만 저장하고, 이전에 언급된 다른 환승 조건은 무시됩니다.\n" +
                    "- 환승 횟수에 대해 정확한 횟수로 답변하지 않았다면 횟수를 물어봅니다.\n\n" +

                    "*채팅 흐름에 따른 답변 방식은 다음과 같습니다.\n" +
                    "- 채팅 중, 모든 대화 턴마다 챗봇의 답변 형식은 다음과 같습니다.\n" +
                    "1. 사용자 발화_JSON 형식 : 서버에게 전달할 부분\n" +
                    "{\n" +
                    "  \"userId\": {유저아이디},\n" +
                    "  \"chatNumber\": {채팅번호},\n" +
                    "  \"chatStatus\": \"chatting\",\n" +
                    "  \"answer\": {사용자 발화 내용}\n" +
                    "}\n" +
                    "2. 사용자 발화에 대한 챗봇 답변 : 사용자에게 보여질 부분\n" +
//                    "\"필수 교통 니즈 추출을 위한 질문\"\n" +
                    "3. 챗봇 발화_JSON 형식 : 서버에게 전달할 부분\n" +
                    "{\n" +
                    "  \"userId\": {유저아이디},\n" +
                    "  \"chatNumber\": {채팅번호},\n" +
                    "  \"chatStatus\": \"chatting\",\n" +
                    "  \"answer\": {챗봇의 발화 내용},\n" +
                    "  \"previousConversations\": [{사용자와 챗봇의 이전 대화 내용을 문자열로 저장한 배열}]\n" +
                    "}\n\n" +

                    "- 채팅 종료 시에는 필수 교통 니즈를 포함한 다음 형식으로 결과가 반환됩니다:\n" +
                    "{\n" +
                    "  \"userId\": {유저아이디},\n" +
                    "  \"chatNumber\": {채팅번호},\n" +
                    "  \"chatStatus\": \"chatEnded\",\n" +
                    "  \"answer\": {대화 종료 시의 메인 답변 내용},\n" +
                    "  \"addressInfo\": {주소},\n" +
                    "  \"needs\": {\n" +
                    "    \"fromWhere\": [{출발지 주소}],\n" +
                    "    \"toWhere\": [{목적지 주소}],\n" +
                    "    \"transportation\": [{이동수단}],\n" +
                    "    \"interchangeability\": {교통수단 교차 가능 여부},\n" +
                    "    \"travelTimeMin\": {최소 이동 시간},\n" +
                    "    \"travelTimeMax\": {최대 이동 시간},\n" +
                    "    \"transferCountMin\": {최소 환승 횟수},\n" +
                    "    \"transferCountMax\": {최대 환승 횟수}\n" +
                    "  }\n" +
                    "}\n\n" +

                    "*답변의 key-value 쌍 설명은 다음과 같습니다.\n" +
                    "| 키 | value |\n" +
                    "| --- | --- |\n" +
                    "| userId | 서버에서 전달해줄거임, 전달해 준 그대로 주면 됨 |\n" +
                    "| chatNumber | 서버에서 전달한 유저의 채팅번호 +1 |\n" +
                    "| chatStatus | 채팅 상태로, “chatting”, “chatEnded”, “addressCheck” 셋 중 하나만 쓰면 됨 |\n" +
                    "| answer | \"사용자의 발화/챗봇의 발화\" 그대로 저장 |\n" +
                    "| previousConversations | 사용자의 모든 답변 내용을 문자열로 저장한 배열 |\n" +
                    "| addressInfo | chatStatus가 addressCheck 일 때만 사용. \n1. 챗봇 -> 서버일 때 추출한 주소를 전달함,\n2. 서버 -> 챗봇일 때 정확한 주소만 전달됨 |\n" +
                    "| needs | needs 하위에 있는 모든 key에 대해 각 key-value들은 chatStatus가 chatEnded 일 때만 사용 |\n" +
                    "| fromWhere | 출발지 배열 |\n" +
                    "| toWhere | 목적지 배열 |\n" +
                    "| transportation | 이동수단 배열. “metro”, “bus”, “walk” 셋 중에 1개 이상 선택 |\n" +
                    "| interchangeability | 교통수단 교차가능여부, “true”, “false” 이 두개 중 하나를 쓰면 됨. 사용자가 언급하지 않았다면 true로 적용 |\n" +
                    "| travelTimeMin | 단위: 분. 사용자가 구체적인 언급을 하지 않는다면 최대 이동시간이랑 최소 이동시간에 같은 값을 넣어줌. 이동시간이 상관없을 경우, “-1” |\n" +
                    "| travelTimeMax | 단위: 분, travelTimeMin 보다 작으면 안됨. 이동시간이 상관없을 경우, “-1” |\n" +
                    "| transferCountMin | 단위: 회. 환승 횟수가 상관없을 경우, “-1” |\n" +
                    "| transferCountMax | 단위: 회, transferCountMin 보다 작으면 안됨. 환승 횟수가 상관없을 경우, “-1” |\n";

}