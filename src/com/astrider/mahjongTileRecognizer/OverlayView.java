package com.astrider.mahjongTileRecognizer;

import java.math.BigDecimal;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Align;
import android.view.View;

public class OverlayView extends View {
	Paint paint = new Paint();
	String[] tiles;
	String currentMethod;
	float[] similarities;
	float[][] coords;
	
	public OverlayView(Context context) {
		super(context);
		// TODO Auto-generated constructor stub
	}
	
	protected void onDraw(Canvas canvas) {
		coords = CaptureHelper.getTileCoords(getWidth(), getHeight());
		
		drawBackground(canvas);
		drawLines(canvas);
		
		if(tiles != null && similarities != null) {
			drawResult(canvas);
		}
	}
	
	public void setCurrentMethod(String method) {
		this.currentMethod = method;
		invalidate();
	}
	
	public void setResult(String[] tiles, float[] similarities) {
		this.tiles = tiles;
		this.similarities = similarities;
		invalidate();
	}
	
	private void drawBackground(Canvas canvas) {
		paint.setColor(Color.BLACK);
		canvas.drawRect(0, coords[0][3] + 1, getWidth(), getHeight(), paint);
		
		paint.setColor(Color.WHITE);
		paint.setTextSize(30.0f);
		paint.setTextAlign(Align.CENTER);
		canvas.drawText("赤い枠線に牌が収まるように調整して下さい。", getWidth() / 2, getHeight() - 10, paint);
		canvas.drawText("長押しでオートフォーカス、離して撮影", getWidth() / 2, getHeight() - 40, paint);
		
		if(currentMethod != null) {
			paint.setTextAlign(Align.LEFT);
			canvas.drawText(currentMethod, 0, getHeight() - 10, paint);
		}
	}
	
	private void drawLines(Canvas canvas) {
		paint.setColor(Color.RED);
		float width = getWidth();
		float height = getHeight();
		
		float[][] coords = CaptureHelper.getTileCoords(width, height);
		for (int i = 0; i < coords.length; i++) {
			canvas.drawLine(coords[i][0], coords[i][3], coords[i][0], coords[i][1], paint);
			canvas.drawLine(coords[i][2], coords[i][3], coords[i][2], coords[i][1], paint);
			canvas.drawLine(coords[i][0], coords[i][3], coords[i][2], coords[i][3], paint);
		}
	}

	private void drawCrosshair(Canvas canvas) {
		// set crosshair color to red
		paint.setColor(Color.RED);
		
		drawBackground(canvas);
		
		// draw crosshair
		for (int i = 0; i < coords.length; i++) {
			canvas.drawLine(coords[i][0], coords[i][1], coords[i][0], coords[i][3], paint);
			canvas.drawLine(coords[i][0], coords[i][1], coords[i][2], coords[i][1], paint);
			canvas.drawLine(coords[i][2], coords[i][3], coords[i][0], coords[i][3], paint);
			canvas.drawLine(coords[i][2], coords[i][3], coords[i][2], coords[i][1], paint);
		}
	}
	
	private void drawResult(Canvas canvas) {
		float width = getWidth();
		float height = getHeight();
		float[][] coords = CaptureHelper.getTileCoords(width, height);
		float[] unitSize = CaptureHelper.getUnitSize(width, height);
		
		paint.setColor(Color.WHITE);
		paint.setTextSize(20.0f);
		paint.setTextAlign(Align.CENTER);
		for (int i = 0; i < tiles.length; i++) {
			float x = coords[i][0] + unitSize[0] / 2;
			float y = coords[i][3] + 20.0f;
			canvas.drawText(tiles[i], x, y, paint);
			
			// round up
			BigDecimal bd = new BigDecimal(similarities[i]);
			BigDecimal rounded = bd.setScale(3, BigDecimal.ROUND_HALF_UP);
			
			canvas.drawText(String.valueOf(rounded.doubleValue()), x, y + 20, paint);
		}
		invalidate();
	}
}
