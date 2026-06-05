package com.zc.iflyzcragback.service.rag.skill;

import java.time.LocalDate;

public interface WeatherLookupService {
    String query(String city, LocalDate date);
}
