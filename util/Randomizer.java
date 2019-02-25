package com.jabulba.discordmanager.util;

import java.util.Random;

public class Randomizer
{
	private static Random random = new Random();

	private static long lastCycle = System.currentTimeMillis();

	/**
	 * @see Random#nextInt()
	 */
	public static int nextInt()
	{
		cycleRandomness();
		return random.nextInt();
	}

	/**
	 * @see Random#nextInt(int)
	 */
	public static int nextInt(int i)
	{
		cycleRandomness();
		return random.nextInt(i);
	}

	/**
	 * @see Random#nextFloat()
	 */
	public static float nextFloat()
	{
		cycleRandomness();
		return random.nextFloat();
	}

	/**
	 * @see Random#nextDouble()
	 */
	public static double nextDouble()
	{
		cycleRandomness();
		return random.nextDouble();
	}

	/**
	 * @see Random#nextBoolean()
	 */
	public static boolean nextBoolean()
	{
		cycleRandomness();
		return random.nextBoolean();
	}

	private static void cycleRandomness()
	{
		if (System.currentTimeMillis() - lastCycle > 1500)
		{
			random = new Random();
			lastCycle = System.currentTimeMillis();
		}
	}

	/**
	 * Randomizes a chance between 0 and 100
	 *
	 * @param chance
	 * 		the % chance to be true [0-100]
	 */
	public static boolean chancePercent(double chance)
	{
		return Randomizer.nextDouble() < chance / 100;

	}
}
