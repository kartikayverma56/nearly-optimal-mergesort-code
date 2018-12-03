package wildinter.net.mergesort;

import java.util.Arrays;
import java.util.Random;

import static wildinter.net.mergesort.MergesAndRuns.extendAndReverseRunRight;
import static wildinter.net.mergesort.MergesAndRuns.extendWeaklyIncreasingRunRight;
import static wildinter.net.mergesort.MergesAndRuns.mergeRuns;

/**
 * Powersort implementation as described in the paper.
 *
 * Natural runs are extended to minRunLen if needed before we continue
 * merging.
 * Unless useMsbMergeType is false, node powers are computed using
 * a most-significant-bit trick;
 * otherwise a loop is used.
 * If onlyIncreasingRuns is true, only weakly increasing runs are picked up.
 *
 * @author Sebastian Wild (wild@uwaterloo.ca)
 */
public class PowerSort implements Sorter {

	private final boolean useMsbMergeType;
	private final boolean onlyIncreasingRuns;
	private final int myMinRunLen;

	private static int minRunLen = 16;

	public PowerSort(final boolean useMsbMergeType, final boolean onlyIncreasingRuns, final int minRunLen) {
		if (!useMsbMergeType && onlyIncreasingRuns)
			throw new UnsupportedOperationException();
		if (minRunLen > 1 && (!useMsbMergeType || onlyIncreasingRuns))
			throw new UnsupportedOperationException();
		this.useMsbMergeType = useMsbMergeType;
		this.onlyIncreasingRuns = onlyIncreasingRuns;
		this.myMinRunLen = minRunLen;
	}

	@Override
	public void sort(final int[] A, final int left, final int right) {
		minRunLen = myMinRunLen;
		if (useMsbMergeType) {
			if (onlyIncreasingRuns)
				powersortIncreasingOnlyMSB(A, left, right);
			else
				powersort(A, left, right);
		} else {
			powersortBitWise(A, left, right);
		}
	}

	private static int nodePower(int left, int right, int startA, int startB, int endB) {
		int n = (right - left + 1);
		long l = (long) startA + (long) startB - ((long) left << 1); // 2*middleA
		long r = (long) startB + (long) endB + 1 - ((long) left << 1); // 2*middleB
		int a = (int) ((l << 30) / n); // middleA / 2n
		int b = (int) ((r << 30) / n); // middleB / 2n
		return Integer.numberOfLeadingZeros(a ^ b);
	}

	private static int nodePowerBitwise(int left, int right, int startA, int startB, int endB) {
		assert right < (1 << 30); // otherwise nt2, l and r will overflow
		final int n = right - left + 1;
		int l = startA - (left << 1) + startB ;
		int r = startB - (left << 1) + endB + 1 ;
		// a and b are given by l/nt2 and r/nt2, both are in [0,1).
		// we have to find the number of common digits in the
		// binary representation in the fractional part.
		int nCommonBits = 0;
		boolean digitA = l >= n, digitB = r >= n;
		while ( digitA == digitB ) {
			++nCommonBits;
//			if (digitA) { l -= n; r -= n; }
			l -= digitA ? n : 0; r -= digitA ? n : 0;
			l <<= 1; r <<= 1;
			digitA = l >= n; digitB = r >= n;
		}
		return nCommonBits + 1;
	}

	private static int nodePowerLoop(int left, int right, int startA, int startB, int endB) {
		assert right < (1 << 30); // otherwise nt2 will overflow
		int nt2 = (right - left + 1) << 1; // 2*n
		int n1 = startB - startA, n2 = endB - startB + 1;
		long a = (startA << 1) + n1 - (left << 1);
		long b = (startB << 1) + n2 - (left << 1);
		int k = 0;
		while (b-a <= nt2 && a / nt2 == b / nt2) {
			++k;
			a <<= 1;
			b <<= 1;
		}
		return k;
	}


	private static int NULL_INDEX = Integer.MIN_VALUE;

	public static void powersort(int[] A, int left, int right) {
		int n = right - left + 1;
		int lgnPlus2 = log2(n) + 2;
		int[] leftRunStart = new int[lgnPlus2], leftRunEnd = new int[lgnPlus2];
		Arrays.fill(leftRunStart,NULL_INDEX);
		int top = 0;
		int[] buffer = new int[n];

		int startA = left, endA = extendAndReverseRunRight(A, startA, right);
		// extend to minRunLen
		int lenA = endA - startA + 1;
		if (lenA < minRunLen) {
			endA = Math.min(right, startA + minRunLen-1);
			Insertionsort.insertionsort(A, startA, endA, lenA);
		}
		while (endA < right) {
			int startB = endA + 1, endB = extendAndReverseRunRight(A, startB, right);
			// extend to minRunLen
			int lenB = endB - startB + 1;
			if (lenB < minRunLen) {
				endB = Math.min(right, startB + minRunLen-1);
				Insertionsort.insertionsort(A, startB, endB, lenB);
			}
			int k = nodePower(left, right, startA, startB, endB);
			assert k != top;
			for (int l = top; l > k; --l) {
				if (leftRunStart[l] == NULL_INDEX) continue;
				mergeRuns(A, leftRunStart[l], leftRunEnd[l]+1, endA, buffer);
				startA = leftRunStart[l];
				leftRunStart[l] = NULL_INDEX;
			}
			// store left half of merge between A and B
			leftRunStart[k] = startA; leftRunEnd[k] = endA;
			top = k;
			startA = startB; endA = endB;
		}
		assert endA == right;
		for (int l = top; l > 0; --l) {
			if (leftRunStart[l] == NULL_INDEX) continue;
			mergeRuns(A, leftRunStart[l], leftRunEnd[l]+1, right, buffer);
		}
	}

	public static void powersortBitWise(int[] A, int left, int right) {
		int n = right - left + 1;
		int lgnPlus2 = log2(n) + 2;
		int[] leftRunStart = new int[lgnPlus2], leftRunEnd = new int[lgnPlus2];
		Arrays.fill(leftRunStart,NULL_INDEX);
		int top = 0;
		int[] buffer = new int[n];

		int startA = left, endA = extendAndReverseRunRight(A, startA, right);
		while (endA < right) {
			int startB = endA + 1, endB = extendAndReverseRunRight(A, startB, right);
			int k = nodePowerBitwise(left, right, startA, startB, endB);
			assert k != top;
			// clear left subtree bottom-up if needed
			for (int l = top; l > k; --l) {
				if (leftRunStart[l] == NULL_INDEX) continue;
				mergeRuns(A, leftRunStart[l], leftRunEnd[l]+1, endA, buffer);
				startA = leftRunStart[l];
				leftRunStart[l] = NULL_INDEX;
			}
			// store left half of merge between A and B
			leftRunStart[k] = startA; leftRunEnd[k] = endA;
			top = k;
			startA = startB; endA = endB;
		}
		assert endA == right;
		for (int l = top; l > 0; --l) {
			if (leftRunStart[l] == NULL_INDEX) continue;
			mergeRuns(A, leftRunStart[l], leftRunEnd[l]+1, right, buffer);
		}
	}

	public static void powersortIncreasingOnlyMSB(int[] A, int left, int right) {
		int n = right - left + 1;
		int lgnPlus2 = log2(n) + 2;
		int[] leftRunStart = new int[lgnPlus2], leftRunEnd = new int[lgnPlus2];
		Arrays.fill(leftRunStart,NULL_INDEX);
		int top = 0;
		int[] buffer = new int[n];

		int startA = left, endA = extendWeaklyIncreasingRunRight(A, startA, right);
		while (endA < right) {
			int startB = endA + 1, endB = extendWeaklyIncreasingRunRight(A, startB, right);
			int k = nodePower(left, right, startA, startB, endB);
			assert k != top;
			// clear left subtree bottom-up if needed
			for (int l = top; l > k; --l) {
				if (leftRunStart[l] == NULL_INDEX) continue;
				mergeRuns(A, leftRunStart[l], leftRunEnd[l]+1, endA, buffer);
				startA = leftRunStart[l];
				leftRunStart[l] = NULL_INDEX;
			}
			// store left half of merge between A and B
			leftRunStart[k] = startA; leftRunEnd[k] = endA;
			top = k;
			startA = startB; endA = endB;
		}
		assert endA == right;
		for (int l = top; l > 0; --l) {
			if (leftRunStart[l] == NULL_INDEX) continue;
			mergeRuns(A, leftRunStart[l], leftRunEnd[l]+1, right, buffer);
		}
	}


	public static int log2(int n) {
	    if(n == 0) throw new IllegalArgumentException("lg(0) undefined");
	    return 31 - Integer.numberOfLeadingZeros( n );
	}

	@Override
	public String toString() {
		return getClass().getSimpleName()
				+"+minRunLen=" + myMinRunLen
				+ "+msb=" + useMsbMergeType
				+ "+onlyIncRuns=" + onlyIncreasingRuns;
	}

	public static void main(String[] args) {

//		System.out.println("exponent(0f) = " + exponent(0f));
//		System.out.println("exponent(1f) = " + exponent(1f));
//		System.out.println("exponent(4f) = " + exponent(4f));
//		System.out.println("exponent(16f) = " + exponent(16f));
//		System.out.println("exponent(0.25f) = " + exponent(0.25f));
//
		int k;
		k = nodePowerBitwise(1, 100, 10, 20, 25); System.out.println(k);
		k = nodePowerLoop(1, 100, 10, 20, 25); System.out.println(k);
		k = nodePower(1, 100, 10, 20, 25); System.out.println(k);
		System.out.println();

		k = nodePowerBitwise(0, 21, 8, 12, 13); System.out.println(k);
		k = nodePowerLoop(0, 21, 8, 12, 13); System.out.println(k);
		k = nodePower(0, 21, 8, 12, 13); System.out.println(k);
		System.out.println();

		k = nodePowerBitwise(0,21, 19, 20, 20); System.out.println(k);
		k = nodePowerLoop(0,21, 19, 20, 20); System.out.println(k);
		k = nodePower(0,21, 19, 20, 20); System.out.println(k);
		System.out.println();

		k = nodePowerBitwise(0,100*1000*1000,55555555,55555666,55556666); System.out.println(k);
		k = nodePowerLoop(0,100*1000*1000,55555555,55555666,55556666); System.out.println(k);
		k = nodePower(0,100*1000*1000,55555555,55555666,55556666); System.out.println(k);
		System.out.println();

//		System.exit(1);


		// run lengths from example in paper: 5, 3, 3, 14, 1, 2
		int[] A = new int[2*(5+3+3+14+1+2)];
		Inputs.fillWithUpAndDownRuns(A, Arrays.asList(5, 3, 3, 14, 1, 2),2,new Random());
		System.out.println(java.util.Arrays.toString(A));
		minRunLen = 1;
		powersort(A, 0, A.length-1);
		System.out.println(java.util.Arrays.toString(A));

	}
}
