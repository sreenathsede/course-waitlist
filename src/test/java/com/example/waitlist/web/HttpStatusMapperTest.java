package com.example.waitlist.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.http.HttpStatus;

import com.example.waitlist.service.ErrorCode;

/**
 * The HTTP contract lives here, not in the service exceptions.
 */
class HttpStatusMapperTest {

    @ParameterizedTest
    @EnumSource(ErrorCode.class)
    void everyErrorCodeMapsToAStatus(ErrorCode code) {
        assertThat(HttpStatusMapper.statusFor(code)).isNotNull();
    }

    @Test
    void documentedMappings() {
        assertThat(HttpStatusMapper.statusFor(ErrorCode.INVALID_REQUEST)).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(HttpStatusMapper.statusFor(ErrorCode.ALREADY_ENROLLED)).isEqualTo(HttpStatus.CONFLICT);
        assertThat(HttpStatusMapper.statusFor(ErrorCode.ALREADY_WAITLISTED)).isEqualTo(HttpStatus.CONFLICT);
        assertThat(HttpStatusMapper.statusFor(ErrorCode.PREREQUISITE_NOT_MET)).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(HttpStatusMapper.statusFor(ErrorCode.NOT_REGISTERED)).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(HttpStatusMapper.statusFor(ErrorCode.SERVICE_BUSY)).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(HttpStatusMapper.statusFor(ErrorCode.ADMIN_UNAUTHORIZED)).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(HttpStatusMapper.statusFor(ErrorCode.ADMIN_DISABLED)).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
