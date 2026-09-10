package com.example.PrathibaLanka.util;

import java.util.UUID;

public class PinGenerator{
    public static String generate(){
        return UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}