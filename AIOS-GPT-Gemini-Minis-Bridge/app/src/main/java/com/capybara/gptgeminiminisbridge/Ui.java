package com.capybara.gptgeminiminisbridge;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.*;

final class Ui {
    static final int BG = Color.rgb(12,19,26), CARD = Color.rgb(23,34,44), INK = Color.rgb(237,246,248), MUTED = Color.rgb(157,177,188), MINT = Color.rgb(123,232,192);
    static int dp(Context c, int n) { return (int) (c.getResources().getDisplayMetrics().density * n + .5f); }
    static GradientDrawable shape(Context c, int color, int radius) {
        GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(c,radius)); return d;
    }
    static LinearLayout column(Context c) { LinearLayout v = new LinearLayout(c); v.setOrientation(LinearLayout.VERTICAL); return v; }
    static TextView text(Context c, String text, int size, int color) {
        TextView v = new TextView(c); v.setText(text); v.setTextSize(size); v.setTextColor(color);
        v.setLineSpacing(dp(c,3),1); return v;
    }
    static void gap(LinearLayout parent, int size) { View v = new View(parent.getContext()); parent.addView(v,new LinearLayout.LayoutParams(1,dp(parent.getContext(),size))); }
    static LinearLayout card(Context c) {
        LinearLayout v = column(c); v.setPadding(dp(c,20),dp(c,20),dp(c,20),dp(c,20)); v.setBackground(shape(c,CARD,22)); return v;
    }
    static Button button(Context c, String text, boolean primary) {
        Button b = new Button(c); b.setText(text); b.setAllCaps(false); b.setTextSize(15);
        b.setTypeface(Typeface.DEFAULT,Typeface.BOLD); b.setTextColor(primary ? BG : INK);
        b.setBackground(shape(c,primary ? MINT : CARD,16)); b.setMinHeight(dp(c,50));
        b.setPadding(dp(c,16),dp(c,8),dp(c,16),dp(c,8)); b.setStateListAnimator(null); return b;
    }
    static void title(LinearLayout parent, String text, int size) {
        TextView t = text(parent.getContext(),text,size,INK); t.setTypeface(Typeface.DEFAULT,Typeface.BOLD); parent.addView(t);
    }
}
