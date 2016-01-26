package com.cs492.gpsgame;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

public class IntroActivity extends AppCompatActivity {

    Button btnTerrorist;
    Button btnCounterTerrorist;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.intro_layout);

        btnTerrorist = (Button)findViewById(R.id.btnTerrorist);
        btnCounterTerrorist = (Button)findViewById(R.id.btnCT);

        btnTerrorist.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(IntroActivity.this, MainActivity.class);
                intent.putExtra("team", "terrorist");
                startActivity(intent);
            }
        });

        btnCounterTerrorist.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(IntroActivity.this, MainActivity.class);
                intent.putExtra("team", "counter");
                startActivity(intent);
            }
        });

    }


}
