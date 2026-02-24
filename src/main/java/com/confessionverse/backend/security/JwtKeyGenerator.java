package com.confessionverse.backend.security;

import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import javax.crypto.SecretKey;
import java.util.Base64;

public class JwtKeyGenerator {
    public static void main(String[] args) {
        SecretKey key = Keys.secretKeyFor(SignatureAlgorithm.HS512);
        String base64Key = Base64.getEncoder().encodeToString(key.getEncoded());
        System.out.println("Generated Base64 Secret Key: " + base64Key);

      //// System.out.println(new BCryptPasswordEncoder().encode("your password to be hashed"));

    }


}


