package com.hearintent.backend.user;

import com.hearintent.backend.security.AuthenticatedUser;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserRepository userRepository;

    public UserController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @GetMapping("/me")
    public Mono<ResponseEntity<User>> me() {
        return ReactiveSecurityContextHolder.getContext()
                .map(ctx -> (AuthenticatedUser) ctx.getAuthentication().getPrincipal())
                .flatMap(principal -> userRepository.findById(principal.userId()))
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }
}
