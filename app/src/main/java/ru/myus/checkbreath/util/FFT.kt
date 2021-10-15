package ru.myus.checkbreath.util
/*
OC Volume - Java Speech Recognition Engine
Copyright (c) 2002-2004, OrangeCow organization
All rights reserved.
Redistribution and use in source and binary forms,
with or without modification, are permitted provided
that the following conditions are met:
 * Redistributions of source code must retain the
above copyright notice, this list of conditions
and the following disclaimer.
 * Redistributions in binary form must reproduce the
above copyright notice, this list of conditions
and the following disclaimer in the documentation
and/or other materials provided with the
distribution.
 * Neither the name of the OrangeCow organization
nor the names of its contributors may be used to
endorse or promote products derived from this
software without specific prior written
permission.
THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS
AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED
WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS
FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO
EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
(INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA,
OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF
THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
POSSIBILITY OF SUCH DAMAGE.
Contact information:
Please visit http://ocvolume.sourceforge.net.
 */

/**
 * Fast Fourier Transform.
 *
 * last updated on June 15, 2002<br></br>
 * **description:** ru.myus.checkbreath.util.FFT class for real signals. Upon entry, N contains the
 * numbers of points in the DFT, real[] and imaginary[] contain the real and
 * imaginary parts of the input. Upon return, real[] and imaginary[] contain the
 * DFT output. All signals run from 0 to N - 1<br></br>
 * **input:** speech signal<br></br>
 * **output:** real and imaginary part of DFT output
 *
 * @author Danny Su
 * @author Hanns Holger Rutz
 */

class FFT {
    lateinit var real: FloatArray
    lateinit var imag: FloatArray

    /**
     * Performs Fast Fourier Transformation in place.
     */
    fun process(signal: FloatArray) {
        val numPoints = signal.size
        // initialize real & imag array
        real = signal
        imag = FloatArray(numPoints)

        // perform ru.myus.checkbreath.util.FFT using the real & imag array
        val pi = Math.PI
        val numStages = (Math.log(numPoints.toDouble()) / Math.log(2.0)).toInt()
        val halfNumPoints = numPoints shr 1
        var j = halfNumPoints
        // ru.myus.checkbreath.util.FFT time domain decomposition carried out by "bit reversal sorting"
        // algorithm
        var k: Int
        for (i in 1 until numPoints - 2) {
            if (i < j) {
                // swap
                val tempReal = real[j]
                val tempImag = imag[j]
                real[j] = real[i]
                imag[j] = imag[i]
                real[i] = tempReal
                imag[i] = tempImag
            }
            k = halfNumPoints
            while (k <= j) {
                j -= k
                k = k shr 1
            }
            j += k
        }

        // loop for each stage
        for (stage in 1..numStages) {
            var LE = 1
            for (i in 0 until stage) {
                LE = LE shl 1
            }
            val LE2 = LE shr 1
            var UR = 1.0
            var UI = 0.0
            // calculate sine & cosine values
            val SR = Math.cos(pi / LE2)
            val SI = -Math.sin(pi / LE2)
            // loop for each sub DFT
            for (subDFT in 1..LE2) {
                // loop for each butterfly
                var butterfly = subDFT - 1
                while (butterfly <= numPoints - 1) {
                    val ip = butterfly + LE2
                    // butterfly calculation
                    val tempReal = (real[ip] * UR - imag[ip] * UI).toFloat()
                    val tempImag = (real[ip] * UI + imag[ip] * UR).toFloat()
                    real[ip] = real[butterfly] - tempReal
                    imag[ip] = imag[butterfly] - tempImag
                    real[butterfly] += tempReal
                    imag[butterfly] += tempImag
                    butterfly += LE
                }
                val tempUR = UR
                UR = tempUR * SR - UI * SI
                UI = tempUR * SI + UI * SR
            }
        }
    }
}