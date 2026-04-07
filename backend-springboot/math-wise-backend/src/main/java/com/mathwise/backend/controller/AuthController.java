package com.mathwise.backend.controller;

import com.mathwise.backend.dto.AuthResponseDto;
import com.mathwise.backend.dto.LoginRequestDto;
import com.mathwise.backend.dto.RegisterRequestDto;
import com.mathwise.backend.entity.Student;
import com.mathwise.backend.repository.StudentRepository;
import com.mathwise.backend.security.JwtUtil;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
// Kepp cross-origin verification open for local flutter testing. TO CHANGE
@CrossOrigin(origins = "*")
public class AuthController {
    private final StudentRepository studentRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    // IOC by constructor
    public AuthController(StudentRepository studentRepository, PasswordEncoder passwordEncoder, JwtUtil jwtUtil) {
        this.studentRepository = studentRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequestDto registerRequestDto) {
        /*
        To register a new student, we have to verify if they already exist. If they don't then
        create the new user, save to db and generate jwt
        */
        if(studentRepository.findByEmail(registerRequestDto.getEmail()).isPresent()){
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Email already exists");
        }

        Student student = new Student();
        student.setEmail(registerRequestDto.getEmail());
        student.setDisplayName(registerRequestDto.getDisplayName());
        student.setPassword(passwordEncoder.encode(registerRequestDto.getPassword()));

        studentRepository.save(student);

        String token = jwtUtil.generateToken(student.getEmail());
        return ResponseEntity.ok(new AuthResponseDto(token, student.getEmail(), student.getDisplayName()));

    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequestDto loginRequestDto) {
        /*
        To login a student, you need to find the user by mail, verify the hash of the provided password
        with the actual hashed password stored in the db. Then give the response
         */
        Optional<Student> optionalStudent = studentRepository.findByEmail(loginRequestDto.getEmail());

        if(optionalStudent.isEmpty()){
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid email or password");
        }

        Student student = optionalStudent.get();
        if(!passwordEncoder.matches(loginRequestDto.getPassword(), student.getPassword())){
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid email or password");
        }

        String token = jwtUtil.generateToken(student.getEmail());
        return ResponseEntity.ok(new AuthResponseDto(token, student.getEmail(), student.getDisplayName()));
    }
}
