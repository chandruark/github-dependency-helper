package com.example.demo.github.api.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class AllServiceResponse {
    private final String serviceName;

    public AllServiceResponse(String serviceName) {
        this.serviceName = serviceName;
    }
}
