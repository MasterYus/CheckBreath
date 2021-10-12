package ru.myus.checkbreath.util

import kotlin.math.cos
import kotlin.math.sin


class FFT {
    // compute the FFT of x[], assuming its length n is a power of 2
    fun fft(x: Array<Complex?>): Array<Complex?> {
        val n = x.size

        // base case
        if (n == 1) return arrayOf(x[0])

        require(n % 2 == 0) { "n is not a power of 2" }

        // compute FFT of even terms
        val even = arrayOfNulls<Complex>(n / 2)
        for (k in 0 until n / 2) {
            even[k] = x[2 * k]
        }
        val evenFFT = fft(even)

        // compute FFT of odd terms
        val odd = even.clone(); // reuse the array (to avoid n log n space)
        for (k in 0 until n / 2) {
            odd[k] = x[2 * k + 1]
        }
        val oddFFT = fft(odd)

        // combine
        val y = arrayOfNulls<Complex>(n)
        for (k in 0 until n / 2) {
            val kth = -2 * k * Math.PI / n
            val wk = Complex(cos(kth), sin(kth))
            y[k] = evenFFT[k]!!.plus(wk.times(oddFFT[k]!!))
            y[k + n / 2] = evenFFT[k]!!.minus(wk.times(oddFFT[k]!!))
        }
        return y
    }

    // compute the inverse FFT of x[], assuming its length n is a power of 2
    fun ifft(x: Array<Complex?>): Array<Complex?> {
        val n = x.size
        var y = arrayOfNulls<Complex>(n)

        // take conjugate
        for (i in 0 until n) {
            y[i] = x[i]!!.conjugate()
        }

        // compute forward FFT
        y = fft(y)

        // take conjugate again
        for (i in 0 until n) {
            y[i] = y[i]!!.conjugate()
        }

        // divide by n
        for (i in 0 until n) {
            y[i] = y[i]!!.scale(1.0 / n)
        }
        return y
    }

    // compute the circular convolution of x and y
    fun cconvolve(x: Array<Complex?>, y: Array<Complex?>): Array<Complex?> {

        // should probably pad x and y with 0s so that they have same length
        // and are powers of 2
        require(x.size == y.size) { "Dimensions don't agree" }
        val n = x.size

        // compute FFT of each sequence
        val a = fft(x)
        val b = fft(y)

        // point-wise multiply
        val c = arrayOfNulls<Complex>(n)
        for (i in 0 until n) {
            c[i] = a[i]!!.times(b[i]!!)
        }

        // compute inverse FFT
        return ifft(c)
    }

    // compute the linear convolution of x and y
    fun convolve(x: Array<Complex?>, y: Array<Complex?>): Array<Complex?> {
        val ZERO = Complex(0.0, 0.0)
        val a = arrayOfNulls<Complex>(2 * x.size)
        for (i in x.indices) a[i] = x[i]
        for (i in x.size until 2 * x.size) a[i] = ZERO
        val b = arrayOfNulls<Complex>(2 * y.size)
        for (i in y.indices) b[i] = y[i]
        for (i in y.size until 2 * y.size) b[i] = ZERO
        return cconvolve(a, b)
    }

    // compute the DFT of x[] via brute force (n^2 time)
    fun dft(x: Array<Complex>): Array<Complex?> {
        val n = x.size
        val ZERO = Complex(0.0, 0.0)
        val y = arrayOfNulls<Complex>(n)
        for (k in 0 until n) {
            y[k] = ZERO
            for (j in 0 until n) {
                val power = k * j % n
                val kth = -2 * power * Math.PI / n
                val wkj = Complex(Math.cos(kth), Math.sin(kth))
                y[k] = y[k]!!.plus(x[j].times(wkj))
            }
        }
        return y
    }
}