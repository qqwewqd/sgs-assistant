package com.sanguosha.assistant.vo;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PageVO<T> {
    private List<T> records;
    private long total;
    private long page;
    private long pageSize;

    public static <T> PageVO<T> of(List<T> records, long total, long page, long pageSize) {
        return new PageVO<>(records, total, page, pageSize);
    }
}
