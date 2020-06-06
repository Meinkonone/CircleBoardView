package com.konone.circleboardview;

import android.os.Bundle;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private CircleBoardView mCircleBoardView;
    private TextView mTextView;
    private List<Integer> mShowValueIndex = new ArrayList<>();
    private List<String> mTotalValues = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mCircleBoardView = findViewById(R.id.circleView);
        mTextView = findViewById(R.id.value_hint);
        initValue();
        mCircleBoardView.setValueChangeListener(new CircleBoardView.ValueChangeListener() {
            @Override
            public void onValueChange(int valueIndex) {
                mTextView.setText(mTotalValues.get(valueIndex));
            }
        });
        mCircleBoardView.forceFinishScroll();
        mCircleBoardView.setIsSolidInterval(false);
        mCircleBoardView.setMaxIndexOffSet(4);
        mCircleBoardView.needSeparateShow(true);
        mCircleBoardView.setScaleInterval(6);
        mCircleBoardView.setIndexEverInterval(3);
        mCircleBoardView.setValue(mShowValueIndex, mTotalValues);
    }

    private void initValue() {
        for (int i = 0; i <= 20; i++) {
            mTotalValues.add("value " + i);
            if (i % 3 == 0) {
                mShowValueIndex.add(i);
            }
        }
    }
}
