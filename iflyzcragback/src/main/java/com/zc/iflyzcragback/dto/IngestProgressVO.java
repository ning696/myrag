package com.zc.iflyzcragback.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class IngestProgressVO {
    private String status;  // processing / completed / failed
    private Integer processed;
    private Integer total;
}
