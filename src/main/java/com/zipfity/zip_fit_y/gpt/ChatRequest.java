package com.zipfity.zip_fit_y.gpt;

import lombok.*;

import java.util.List;
import java.util.Map;

@Builder
@Getter
@Setter
@ToString
@AllArgsConstructor
public class ChatRequest {

    private String userId;  // 사용자 ID
    private int chatNumber; // 대화 번호
    private String chatStatus;  // 현재 채팅 상태 (예: "chatting", "chatEnded")
    private String answer;  // 사용자가 입력한 대화 내용
    private boolean addressCheck;  // 주소 확인 여부 추가
    private Map<String, Object> requestData;  // 동적인 데이터 처리
    private List<String> previousConversations;  // 이전 대화 기록 (옵션 필드)
}
