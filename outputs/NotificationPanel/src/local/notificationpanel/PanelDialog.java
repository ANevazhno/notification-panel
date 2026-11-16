package local.notificationpanel;
import android.app.*;import android.content.*;import android.graphics.Color;import android.graphics.Typeface;import android.graphics.drawable.*;import android.view.*;import android.widget.*;
/** Shared, scrollable panel sheet. Uses native semantics with entirely app-owned surfaces. */
public final class PanelDialog extends Dialog {
 private final java.util.Map<Integer,Button> buttons=new java.util.HashMap<>();
 private PanelDialog(Context c){super(c);requestWindowFeature(Window.FEATURE_NO_TITLE);}
 public Button getButton(int id){return buttons.get(id);}
 static int dp(Context c,float n){return (int)(n*c.getResources().getDisplayMetrics().density+.5f);}
 static GradientDrawable surface(Context c,int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(c,radius));return d;}
 static TextView label(Context c,String text,int sp,int color){TextView v=new TextView(c);v.setText(text);v.setTextSize(sp);v.setTextColor(color);return v;}
 static void style(View v){Context c=v.getContext();
  if(v instanceof EditText){EditText e=(EditText)v;e.setTextColor(PanelColors.TEXT);e.setHintTextColor(PanelColors.MUTED);e.setTextSize(16);e.setPadding(dp(c,14),dp(c,12),dp(c,14),dp(c,12));e.setBackground(surface(c,0xff172b3d,12));}
  else if(v instanceof Switch){Switch sw=(Switch)v;sw.setTextSize(15);sw.setMinHeight(dp(c,56));sw.setSwitchMinWidth(dp(c,46));sw.setSwitchPadding(dp(c,12));sw.setThumbTintList(android.content.res.ColorStateList.valueOf(0xffeff7ff));sw.setTrackTintList(new android.content.res.ColorStateList(new int[][]{new int[]{android.R.attr.state_checked},new int[]{}},new int[]{0xff579bab,0xff536578}));}
  else if(v instanceof Button){Button b=(Button)v;b.setStateListAnimator(null);b.setElevation(0);b.setAllCaps(false);b.setTextSize(15);b.setTextColor(PanelColors.ACCENT);b.setGravity(Gravity.CENTER_VERTICAL|Gravity.START);b.setMinHeight(dp(c,52));b.setMinimumHeight(dp(c,52));b.setPadding(dp(c,14),dp(c,8),dp(c,14),dp(c,8));b.setBackground(new RippleDrawable(android.content.res.ColorStateList.valueOf(PanelColors.RIPPLE),surface(c,0xff293e52,12),null));}
  if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)style(((ViewGroup)v).getChildAt(i));
 }
 public static final class Builder {
  final Context c;String title="",message;View view;String[] items;int selected=-1;boolean[] checked;DialogInterface.OnClickListener itemClick;DialogInterface.OnMultiChoiceClickListener multi;
  final java.util.LinkedHashMap<Integer,String> labels=new java.util.LinkedHashMap<>();final java.util.Map<Integer,DialogInterface.OnClickListener> callbacks=new java.util.HashMap<>();
  public Builder(Context c){this.c=c;}
  public Builder setTitle(String s){title=s;return this;} public Builder setMessage(String s){message=s;return this;}public Builder setView(View v){view=v;return this;}
  public Builder setItems(String[] a,DialogInterface.OnClickListener l){items=a;itemClick=l;return this;}
  public Builder setSingleChoiceItems(String[] a,int choice,DialogInterface.OnClickListener l){items=a;selected=choice;itemClick=l;return this;}
  public Builder setMultiChoiceItems(String[] a,boolean[] b,DialogInterface.OnMultiChoiceClickListener l){items=a;checked=b;multi=l;return this;}
  public Builder setPositiveButton(String s,DialogInterface.OnClickListener l){return button(BUTTON_POSITIVE,s,l);}public Builder setNegativeButton(String s,DialogInterface.OnClickListener l){return button(BUTTON_NEGATIVE,s,l);}public Builder setNeutralButton(String s,DialogInterface.OnClickListener l){return button(BUTTON_NEUTRAL,s,l);}
  private Builder button(int id,String s,DialogInterface.OnClickListener l){labels.put(id,s);callbacks.put(id,l);return this;}
  public PanelDialog show(){PanelDialog d=create();d.show();return d;}
  public PanelDialog create(){PanelDialog d=new PanelDialog(c);LinearLayout root=new LinearLayout(c);root.setOrientation(1);root.setPadding(dp(c,20),dp(c,16),dp(c,20),dp(c,16));root.setBackground(surface(c,0xff203448,24));
   TextView heading=label(c,title,24,PanelColors.TEXT);heading.setTypeface(Typeface.create("sans-serif-medium",0));heading.setAccessibilityHeading(true);heading.setPadding(0,0,0,dp(c,16));root.addView(heading);
   LinearLayout content=new LinearLayout(c);content.setOrientation(1);
   if(message!=null){TextView body=label(c,message,15,PanelColors.MUTED);body.setTextIsSelectable(true);body.setLineSpacing(dp(c,3),1);body.setPadding(0,0,0,dp(c,16));content.addView(body);}
   if(view!=null){if(view instanceof ScrollView){ScrollView old=(ScrollView)view;View inner=old.getChildAt(0);old.removeView(inner);view=inner;}style(view);content.addView(view);}
   if(items!=null)for(int i=0;i<items.length;i++){final int index=i;
    LinearLayout row=new LinearLayout(c);row.setGravity(Gravity.CENTER_VERTICAL);row.setMinimumHeight(dp(c,56));row.setPadding(dp(c,14),dp(c,10),dp(c,14),dp(c,10));row.setBackground(new RippleDrawable(android.content.res.ColorStateList.valueOf(PanelColors.RIPPLE),surface(c,i==selected?0xff375b70:0xff293e52,12),null));
    TextView text=label(c,items[i],16,PanelColors.TEXT);row.addView(text,new LinearLayout.LayoutParams(0,-2,1));
    if(checked!=null){CheckBox check=new CheckBox(c);check.setChecked(checked[i]);check.setButtonTintList(android.content.res.ColorStateList.valueOf(PanelColors.ACCENT));check.setClickable(false);check.setFocusable(false);row.addView(check);row.setOnClickListener(v->{checked[index]=!checked[index];check.setChecked(checked[index]);row.setContentDescription(items[index]+(checked[index]?", выбрано":", не выбрано"));multi.onClick(d,index,checked[index]);});}
    else{if(i==selected){TextView value=label(c,"Выбрано",12,PanelColors.ACCENT);value.setPadding(dp(c,10),0,0,0);row.addView(value);}row.setOnClickListener(v->{d.dismiss();if(itemClick!=null)itemClick.onClick(d,index);});}
    row.setFocusable(true);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(c,6);content.addView(row,lp);
   }
   ScrollView scroll=new ScrollView(c);scroll.setFillViewport(false);scroll.addView(content);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
   LinearLayout footer=new LinearLayout(c);footer.setPadding(0,dp(c,12),0,0);for(java.util.Map.Entry<Integer,String> entry:labels.entrySet()){int id=entry.getKey();Button b=new Button(c);b.setText(entry.getValue());style(b);b.setGravity(Gravity.CENTER);if(id==BUTTON_POSITIVE)b.setBackground(surface(c,0xff365a70,14));b.setOnClickListener(v->{DialogInterface.OnClickListener cb=callbacks.get(id);if(cb!=null)cb.onClick(d,id);d.dismiss();});d.buttons.put(id,b);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);lp.setMargins(dp(c,3),0,dp(c,3),0);footer.addView(b,lp);}if(labels.isEmpty()){Button close=new Button(c);close.setText("Закрыть");style(close);close.setGravity(Gravity.CENTER);close.setOnClickListener(v->d.dismiss());footer.addView(close,new LinearLayout.LayoutParams(-1,-2));}root.addView(footer);
   int height=(int)(c.getResources().getDisplayMetrics().heightPixels*.82f);int width=Math.min(c.getResources().getDisplayMetrics().widthPixels-dp(c,24),dp(c,560));
   // Measure content to avoid large empty sheets; cap long forms and keep actions visible.
   content.measure(View.MeasureSpec.makeMeasureSpec(width-dp(c,40),View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));heading.measure(View.MeasureSpec.makeMeasureSpec(width-dp(c,40),View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));height=Math.min(height,content.getMeasuredHeight()+heading.getMeasuredHeight()+dp(c,112));
   d.setContentView(root);Window w=d.getWindow();w.setBackgroundDrawableResource(android.R.color.transparent);w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);WindowManager.LayoutParams params=w.getAttributes();params.width=width;params.height=height;params.gravity=Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL;params.y=dp(c,12);params.dimAmount=.45f;w.setAttributes(params);w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);return d;
  }
 }
}
