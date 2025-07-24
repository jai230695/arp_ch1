// File: src/main/java/com/arp/utils/RandomUtils.java
package com.arp_1.utils;

import java.util.*;
import java.util.stream.IntStream;

/**
 * Utility class for random number generation and randomization operations
 */
public class RandomUtils {

    private static final Random DEFAULT_RANDOM = new Random();

    /**
     * Create a new Random instance with specific seed
     */
    public static Random createSeededRandom(long seed) {
        return new Random(seed);
    }

    /**
     * Generate random integer between min (inclusive) and max (exclusive)
     */
    public static int randomInt(int min, int max) {
        return DEFAULT_RANDOM.nextInt(max - min) + min;
    }

    /**
     * Generate random integer between min (inclusive) and max (exclusive) with
     * specific random instance
     */
    public static int randomInt(Random random, int min, int max) {
        return random.nextInt(max - min) + min;
    }

    /**
     * Generate random double between min (inclusive) and max (exclusive)
     */
    public static double randomDouble(double min, double max) {
        return DEFAULT_RANDOM.nextDouble() * (max - min) + min;
    }

    /**
     * Generate random double between min (inclusive) and max (exclusive) with
     * specific random instance
     */
    public static double randomDouble(Random random, double min, double max) {
        return random.nextDouble() * (max - min) + min;
    }

    /**
     * Generate random boolean with 50% probability
     */
    public static boolean randomBoolean() {
        return DEFAULT_RANDOM.nextBoolean();
    }

    /**
     * Generate random boolean with specific probability
     */
    public static boolean randomBoolean(double probability) {
        return DEFAULT_RANDOM.nextDouble() < probability;
    }

    /**
     * Generate random boolean with specific probability and random instance
     */
    public static boolean randomBoolean(Random random, double probability) {
        return random.nextDouble() < probability;
    }

    /**
     * Select random element from list
     */
    public static <T> T randomChoice(List<T> list) {
        if (list.isEmpty()) {
            throw new IllegalArgumentException("Cannot choose from empty list");
        }
        return list.get(DEFAULT_RANDOM.nextInt(list.size()));
    }

    /**
     * Select random element from list with specific random instance
     */
    public static <T> T randomChoice(Random random, List<T> list) {
        if (list.isEmpty()) {
            throw new IllegalArgumentException("Cannot choose from empty list");
        }
        return list.get(random.nextInt(list.size()));
    }

    /**
     * Select random element from array
     */
    public static <T> T randomChoice(T[] array) {
        if (array.length == 0) {
            throw new IllegalArgumentException("Cannot choose from empty array");
        }
        return array[DEFAULT_RANDOM.nextInt(array.length)];
    }

    /**
     * Select multiple random elements from list (without replacement)
     */
    public static <T> List<T> randomChoices(List<T> list, int count) {
        if (count > list.size()) {
            throw new IllegalArgumentException("Cannot choose more elements than available");
        }

        List<T> shuffled = new ArrayList<>(list);
        Collections.shuffle(shuffled, DEFAULT_RANDOM);
        return shuffled.subList(0, count);
    }

    /**
     * Select multiple random elements from list (without replacement) with specific
     * random instance
     */
    public static <T> List<T> randomChoices(Random random, List<T> list, int count) {
        if (count > list.size()) {
            throw new IllegalArgumentException("Cannot choose more elements than available");
        }

        List<T> shuffled = new ArrayList<>(list);
        Collections.shuffle(shuffled, random);
        return shuffled.subList(0, count);
    }

    /**
     * Shuffle list in place
     */
    public static <T> void shuffle(List<T> list) {
        Collections.shuffle(list, DEFAULT_RANDOM);
    }

    /**
     * Shuffle list in place with specific random instance
     */
    public static <T> void shuffle(Random random, List<T> list) {
        Collections.shuffle(list, random);
    }

    /**
     * Create shuffled copy of list
     */
    public static <T> List<T> shuffled(List<T> list) {
        List<T> copy = new ArrayList<>(list);
        Collections.shuffle(copy, DEFAULT_RANDOM);
        return copy;
    }

    /**
     * Create shuffled copy of list with specific random instance
     */
    public static <T> List<T> shuffled(Random random, List<T> list) {
        List<T> copy = new ArrayList<>(list);
        Collections.shuffle(copy, random);
        return copy;
    }

    /**
     * Generate random permutation of integers from 0 to n-1
     */
    public static List<Integer> randomPermutation(int n) {
        List<Integer> permutation = IntStream.range(0, n)
                .boxed()
                .collect(java.util.stream.Collectors.toList());
        shuffle(permutation);
        return permutation;
    }

    /**
     * Generate random permutation with specific random instance
     */
    public static List<Integer> randomPermutation(Random random, int n) {
        List<Integer> permutation = IntStream.range(0, n)
                .boxed()
                .collect(java.util.stream.Collectors.toList());
        shuffle(random, permutation);
        return permutation;
    }

    /**
     * Generate random sample from range [0, n) with specific size
     */
    public static Set<Integer> randomSample(int n, int sampleSize) {
        if (sampleSize > n) {
            throw new IllegalArgumentException("Sample size cannot be larger than population");
        }

        Set<Integer> sample = new HashSet<>();
        while (sample.size() < sampleSize) {
            sample.add(DEFAULT_RANDOM.nextInt(n));
        }
        return sample;
    }

    /**
     * Generate random sample with specific random instance
     */
    public static Set<Integer> randomSample(Random random, int n, int sampleSize) {
        if (sampleSize > n) {
            throw new IllegalArgumentException("Sample size cannot be larger than population");
        }

        Set<Integer> sample = new HashSet<>();
        while (sample.size() < sampleSize) {
            sample.add(random.nextInt(n));
        }
        return sample;
    }

    /**
     * Weighted random selection
     */
    public static <T> T weightedChoice(List<T> items, List<Double> weights) {
        if (items.size() != weights.size()) {
            throw new IllegalArgumentException("Items and weights lists must have same size");
        }

        double totalWeight = weights.stream().mapToDouble(Double::doubleValue).sum();
        double randomValue = DEFAULT_RANDOM.nextDouble() * totalWeight;

        double cumulativeWeight = 0.0;
        for (int i = 0; i < items.size(); i++) {
            cumulativeWeight += weights.get(i);
            if (randomValue <= cumulativeWeight) {
                return items.get(i);
            }
        }

        // Fallback (shouldn't happen with proper weights)
        return items.get(items.size() - 1);
    }

    /**
     * Weighted random selection with specific random instance
     */
    public static <T> T weightedChoice(Random random, List<T> items, List<Double> weights) {
        if (items.size() != weights.size()) {
            throw new IllegalArgumentException("Items and weights lists must have same size");
        }

        double totalWeight = weights.stream().mapToDouble(Double::doubleValue).sum();
        double randomValue = random.nextDouble() * totalWeight;

        double cumulativeWeight = 0.0;
        for (int i = 0; i < items.size(); i++) {
            cumulativeWeight += weights.get(i);
            if (randomValue <= cumulativeWeight) {
                return items.get(i);
            }
        }

        // Fallback (shouldn't happen with proper weights)
        return items.get(items.size() - 1);
    }

    /**
     * Generate random Gaussian (normal) distribution value
     */
    public static double randomGaussian(double mean, double standardDeviation) {
        return DEFAULT_RANDOM.nextGaussian() * standardDeviation + mean;
    }

    /**
     * Generate random Gaussian with specific random instance
     */
    public static double randomGaussian(Random random, double mean, double standardDeviation) {
        return random.nextGaussian() * standardDeviation + mean;
    }

    /**
     * Generate random exponential distribution value
     */
    public static double randomExponential(double rate) {
        return -Math.log(1.0 - DEFAULT_RANDOM.nextDouble()) / rate;
    }

    /**
     * Generate random exponential with specific random instance
     */
    public static double randomExponential(Random random, double rate) {
        return -Math.log(1.0 - random.nextDouble()) / rate;
    }

    /**
     * Create seed array for reproducible multi-threaded randomization
     */
    public static long[] generateSeedArray(int size, long baseSeed) {
        Random seedGenerator = new Random(baseSeed);
        long[] seeds = new long[size];
        for (int i = 0; i < size; i++) {
            seeds[i] = seedGenerator.nextLong();
        }
        return seeds;
    }

    /**
     * Generate random string of specified length using alphanumeric characters
     */
    public static String randomAlphanumericString(int length) {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(DEFAULT_RANDOM.nextInt(chars.length())));
        }
        return sb.toString();
    }

    /**
     * Generate random string with specific random instance
     */
    public static String randomAlphanumericString(Random random, int length) {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }

    /**
     * Set global random seed (affects DEFAULT_RANDOM)
     */
    public static void setGlobalSeed(long seed) {
        DEFAULT_RANDOM.setSeed(seed);
        LoggingUtils.logDebug("Global random seed set to: " + seed);
    }

    /**
     * Generate time-based seed for randomization
     */
    public static long generateTimeSeed() {
        return System.currentTimeMillis() + System.nanoTime();
    }

    /**
     * Test randomness quality (basic chi-square test for uniform distribution)
     */
    public static double testUniformRandomness(Random random, int samples, int bins) {
        int[] counts = new int[bins];

        for (int i = 0; i < samples; i++) {
            int bin = random.nextInt(bins);
            counts[bin]++;
        }

        double expected = (double) samples / bins;
        double chiSquare = 0.0;

        for (int count : counts) {
            double diff = count - expected;
            chiSquare += (diff * diff) / expected;
        }

        return chiSquare;
    }
}
