package com.mathwise.backend.controller;

import com.mathwise.backend.dto.AuthResponseDto;
import com.mathwise.backend.dto.LoginRequestDto;
import com.mathwise.backend.dto.RegisterRequestDto;
import com.mathwise.backend.entity.Student;
import com.mathwise.backend.exception.EmailAlreadyExistsException;
import com.mathwise.backend.exception.InvalidCredentialsException;
import com.mathwise.backend.repository.StudentRepository;
import com.mathwise.backend.security.JwtUtil;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final StudentRepository studentRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public AuthController(StudentRepository studentRepository,
                           PasswordEncoder passwordEncoder,
                           JwtUtil jwtUtil) {
        this.studentRepository = studentRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    /**
     * Register a new student.
     *
     * <ul>
     *   <li>{@code @Valid} triggers Bean Validation on the request body. Invalid
     *       input becomes a {@code MethodArgumentNotValidException} → 400 with
     *       field-level errors via {@link com.mathwise.backend.exception.GlobalExceptionHandler}.</li>
     *   <li>Email collision throws {@link EmailAlreadyExistsException} → 409
     *       + code {@code EMAIL_ALREADY_EXISTS}. The previous implementation
     *       returned a raw {@code String} body which broke the uniform error
     *       envelope used everywhere else.</li>
     * </ul>
     */
    @PostMapping("/register")
    public ResponseEntity<AuthResponseDto> register(@Valid @RequestBody RegisterRequestDto request) {
        if (studentRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new EmailAlreadyExistsException(request.getEmail());
        }

        Student student = new Student();
        student.setEmail(request.getEmail());
        student.setDisplayName(request.getDisplayName());
        student.setPassword(passwordEncoder.encode(request.getPassword()));
        studentRepository.save(student);

        String token = jwtUtil.generateToken(student.getEmail());
        return ResponseEntity.ok(new AuthResponseDto(token, student.getEmail(), student.getDisplayName()));
    }

    /**
     * Authenticate a student. {@link InvalidCredentialsException} is thrown
     * for both "unknown email" and "wrong password" — leaking which one
     * failed would help attackers enumerate registered emails.
     */
    @PostMapping("/login")
    public ResponseEntity<AuthResponseDto> login(@Valid @RequestBody LoginRequestDto request) {
        Optional<Student> optionalStudent = studentRepository.findByEmail(request.getEmail());
        if (optionalStudent.isEmpty()) {
            throw new InvalidCredentialsException();
        }

        Student student = optionalStudent.get();
        if (!passwordEncoder.matches(request.getPassword(), student.getPassword())) {
            throw new InvalidCredentialsException();
        }

        String token = jwtUtil.generateToken(student.getEmail());
        return ResponseEntity.ok(new AuthResponseDto(token, student.getEmail(), student.getDisplayName()));
    }
}
