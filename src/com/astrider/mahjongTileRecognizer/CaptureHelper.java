package com.astrider.mahjongTileRecognizer;

import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.Map.Entry;

import org.opencv.android.Utils;
import org.opencv.calib3d.Calib3d;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint2f;
import org.opencv.imgproc.Imgproc;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Matrix;
import android.os.Message;
import android.util.Log;

public class CaptureHelper {
	public static final int ASPECT_RATIO = 4/3;
	public static final int DEFAULT_WIDTH = 30;
	public static final int DEFAULT_HEIGHT = DEFAULT_WIDTH * ASPECT_RATIO;
	public static final int TILE_NUM = 14;
	
	Bitmap source;
	Resources res;
	String packageName;
	HashMap<String, Bitmap> templates = new HashMap<String, Bitmap>();
	
	Bitmap[] slicedImages = new Bitmap[TILE_NUM];
	String[] detectedTiles = new String[TILE_NUM];
	double[] difference = new double[TILE_NUM];
	
	public CaptureHelper(Bitmap sourceImage, Resources res, String packageName) {
		System.loadLibrary("orb_feature_detector");
		
		this.source = sourceImage;
		this.res = res;
		this.packageName = packageName;
		sliceImage(sourceImage);
		loadTemplates();
	}
	
	private void sliceImage(Bitmap sourceImage) {
		float[][] coords = getTileCoords(sourceImage.getWidth(), sourceImage.getHeight());
		
		for (int i = 0; i < coords.length; i++) {
			int x = (int) coords[i][0];
			int y = (int) coords[i][1];
			int width = (int) coords[i][2] - x;
			int height = (int) coords[i][3] - y;
			
			Bitmap slicedImage = Bitmap.createBitmap(sourceImage, x, y, width, height);
			slicedImages[i] = slicedImage;
//			slicedImages[i] = convertResolution(slicedImage);
			
//			slicedImage.recycle();
//			slicedImage = null;
		}
		
//		sourceImage.recycle();
//		sourceImage = null;
	}
	
	private void loadTemplates() {
		int[] widths = new int[5];
		int[] heights = new int[5];
		int[][] rgbas = new int[5][];
		
		int[] ids = {R.drawable.w1};
		
		for (int i=0; i<ids.length; i++) {
//			int id = res.getIdentifier(key, "drawable", packageName);
			Bitmap src = BitmapFactory.decodeResource(res, ids[i]);
//				templates.put(key, src);
//				Bitmap value = convertResolution(src);
//				templates.put(key, value);
			
			// ŠwK—p
			widths[i] = src.getWidth();
			heights[i] = src.getHeight();
			rgbas[i] = new int[widths[i] * heights[i]];
			src.getPixels(rgbas[i], 0, widths[i], 0, 0, widths[i], heights[i]);

			src.recycle();
			src = null;
		}
		
		setTrainingImages(widths, heights, rgbas, 1);
	}
	
	public Bitmap[] getSlicedImages() {
		return slicedImages;
	}
	
	public static Bitmap detectRectangle(Bitmap source) {
		
		return source;
	}
	
	public String[] identifyTiles() {
		for (int i=0; i<slicedImages.length; i++) {
			// load next sliced tile image
			Bitmap target = convertResolution(slicedImages[i]);
			
			// prepare variables
			double tmpVal = 0;
			double minDiff = 100;
			String tileType = "";
			
			// compare target with each templates
			for (Entry<String, Bitmap> entry : templates.entrySet()) {
				tmpVal = compareImage(entry.getValue(), target);
				
				if(tmpVal < minDiff) {
					minDiff = tmpVal;
					tileType = entry.getKey();
				}
			}
			
			detectedTiles[i] = tileType;
			difference[i] = minDiff;
			
			ByteArrayOutputStream bos = new ByteArrayOutputStream();
			target.compress(CompressFormat.JPEG, 100, bos);
			int id = detectImage(target.getWidth(), target.getHeight(), bos.toByteArray());
			Log.d("tag", String.valueOf(id));
		}
		
		return detectedTiles;
	}
	
	public double[] getDifferences() {
		return difference;
	}
	
	public static Bitmap convertResolution(Bitmap src) {
		// check current resolution
		int srcWidth = src.getWidth();
		int srcHeight = src.getHeight();
		float dstWidth = DEFAULT_WIDTH;
		float dstHeight = DEFAULT_HEIGHT;
		
		// return if convert is not needed
		if(srcWidth == dstWidth && srcHeight == dstHeight) {
			return src;
		}
		
		// convert resolution
		Matrix matrix = new Matrix();
		matrix.postScale(dstWidth / srcWidth, dstHeight / srcHeight);
		Bitmap dst = Bitmap.createBitmap(src, 0, 0, srcWidth, srcHeight, matrix, true);
		
		return dst;
	}

	public static double compareImage(Bitmap src, Bitmap target) {
		// check and convert resolution
		src = convertResolution(src);
		target = convertResolution(target);
		
		// get pixels
		int[] srcPixels = new int[DEFAULT_WIDTH * DEFAULT_HEIGHT];
		src.getPixels(srcPixels, 0, DEFAULT_WIDTH, 0, 0, DEFAULT_WIDTH, DEFAULT_HEIGHT);

		int[] targetPixels = new int[DEFAULT_WIDTH * DEFAULT_HEIGHT];
		target.getPixels(targetPixels, 0, DEFAULT_WIDTH, 0, 0, DEFAULT_WIDTH, DEFAULT_HEIGHT);
		
		// calculate diff
		int srcColor = 0;
		int targetColor = 0;
		int rDiff = 0;
		int gDiff = 0;
		int bDiff = 0;
		int rDiffSum = 0;
		int gDiffSum = 0;
		int bDiffSum = 0;
		
		for (int x = 0; x < DEFAULT_WIDTH; x++) {
			for (int y = 0; y < DEFAULT_HEIGHT; y++) {
				srcColor = srcPixels[x + y*DEFAULT_WIDTH];
				targetColor = targetPixels[x + y*DEFAULT_WIDTH];
				
				rDiff = Color.red(srcColor) - Color.red(targetColor);
				gDiff = Color.green(srcColor) - Color.green(targetColor);
				bDiff = Color.blue(srcColor) - Color.blue(targetColor);
				
				rDiffSum += Math.pow(rDiff, 2);
				rDiffSum += Math.pow(gDiff, 2);
				rDiffSum += Math.pow(bDiff, 2);
			}
		}
		
		double maxDiff = Math.sqrt(DEFAULT_WIDTH * DEFAULT_HEIGHT * Math.pow(255, 2));
		double denominator = 3 * maxDiff;
		double numerator = Math.sqrt(rDiffSum) + Math.sqrt(gDiffSum) + Math.sqrt(bDiffSum);
		
		return numerator / denominator * 100;
	}

	public static ColorMatrixColorFilter setContrast(float contrast) {
	    float scale = contrast + 1.f;
	    float translate = (-.5f * scale + .5f) * 255.f;
	    float[] array = new float[] {
	        scale, 0, 0, 0, translate,
	        0, scale, 0, 0, translate,
	        0, 0, scale, 0, translate,
	        0, 0, 0, 1, 0};
	    ColorMatrix matrix = new ColorMatrix(array);
	    ColorMatrixColorFilter filter = new ColorMatrixColorFilter(matrix);
	    return filter;
	}
	
	public static Bitmap binarize(Bitmap src) {
		int width = src.getWidth();
		int height = src.getHeight();
		int[] pixels = new int[width * height]; 
		src.getPixels(pixels, 0, width, 0, 0, width, height);
		
		for (int x = 0; x < width; x++) {
			for (int y = 0; y < height; y++) {
				int color = pixels[x + y*width];
				
				int r = Color.red(color);
				int g = Color.green(color);
				int b = Color.blue(color);
				
				int average;
				average = (r + g + b) / 3;
				if(average < 160) {
					r = g = b = 0;
				} else {
					r = g = b = 255;
				}
				
				pixels[x + y*width] = Color.rgb(r, g, b);
			}
		}
		
		src.setPixels(pixels, 0, width, 0, 0, width, height);
		
		return src;
	}

	public static float[][] getTileCoords(float width, float height) {
		float[][] tiles = new float[TILE_NUM][4];
		
		float[] unitSize = getUnitSize(width, height);
		float unitWidth = unitSize[0];
		float unitHeight = unitSize[1];
		
		float left = unitWidth;
		float bottom = height / 2;
		
		for (int i = 0; i < tiles.length; i++) {
			tiles[i][0] = left + unitWidth*i;
			tiles[i][1] = bottom - unitHeight;
			tiles[i][2] = left + unitWidth*(i+1);
			tiles[i][3] = bottom;
		}
		
		return tiles;
	}
	
	public static float[] getUnitSize(float width, float height) {
		float[] unitSize = new float[2];
		float unitWidth = width / 16;
		float unitHeight = unitWidth * 4/3;
		
		unitSize[0] = unitWidth;
		unitSize[1] = unitHeight;
		
		return unitSize;
	}
	
	public native void dummy();
	public native int detectImage(int width, int height, byte[] data);
	public native void setTrainingImages(int[] widths, int[] heights, int[][] rgbas, int imageNum);
}
