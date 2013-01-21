package com.astrider.mahjongTileRecognizer;

public class ScoringHelper {
	public static final int TILE_NUM = 14;
	int[] tiles = new int[TILE_NUM];
	
	public ScoringHelper(int[] tiles) {
		this.tiles = riipai(tiles);
	}
	
	public static int[] riipai(int[] tiles) {
		int tmp;
		for (int i = 0; i < tiles.length; i++) {
			for (int j=tiles.length-1; j > i; j--) {
				if(tiles[j-1] > tiles[j]) {
					tmp = tiles[j-1];
					tiles[j-1] = tiles[j];
					tiles[j] = tmp; 
				}
			}
		}
		return tiles;
	}
}
