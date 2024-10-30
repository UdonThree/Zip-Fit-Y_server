package com.zipfity.zip_fit_y.gpt;

public enum ChatStatus {
    CHATTING,     // 대화가 진행 중일 때
    CHAT_ENDED,   // 대화가 종료되었을 때
    ADDRESS_CHECK // 사용자의 주소를 확인하는 상태일 때
}