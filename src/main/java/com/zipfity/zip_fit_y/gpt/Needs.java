package com.zipfity.zip_fit_y.gpt;

import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Builder
@ToString
@Getter
@Setter
@AllArgsConstructor
public class Needs {
    private List<String> fromWhere;
    private List<String> toWhere;
    private List<String> transportation;
    private boolean interchangeability;
    private int travelTimeMin;
    private int travelTimeMax;
    private int transferCountMin;
    private int transferCountMax;

    // 기본 생성자
    public Needs() {
        this.fromWhere = new ArrayList<>(); // 빈 배열로 초기화
        this.toWhere = new ArrayList<>();
        this.transportation = new ArrayList<>();
        this.interchangeability = true; // 기본값
        this.travelTimeMin = -1;
        this.travelTimeMax = -1;
        this.transferCountMin = -1;
        this.transferCountMax = -1;
    }
}
