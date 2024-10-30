package com.zipfity.zip_fit_y.gpt;

import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Builder
@ToString
@Getter
@Setter
@AllArgsConstructor
public class ChatResponse {
    private String userId;
    private int chatNumber;
    private String chatStatus;
    private String answer;
    private List<String> previousConversations;

    // addressCheck 상태일 때만 필요한 필드
    private String addressInfo;

    // chatEnded 상태일 때만 필요한 필드: 필수 교통 니즈
    private Needs needs; // 필수 교통 니즈 객체

    // 기본 생성자
    public ChatResponse() {
        this.previousConversations = new ArrayList<>();
        this.needs = new Needs(); // 기본 생성자로 needs 초기화
    }

}