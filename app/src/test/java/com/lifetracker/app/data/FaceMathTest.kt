package com.lifetracker.app.data

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FaceMathTest {
    private fun v(vararg x: Float) = floatArrayOf(*x)

    @Test
    fun cosineIsOneForTheSameDirectionAndZeroAtRightAngles() {
        assertEquals(1f, FaceMath.cosine(v(1f, 2f, 3f), v(2f, 4f, 6f)), 1e-6f)
        assertEquals(0f, FaceMath.cosine(v(1f, 0f), v(0f, 1f)), 1e-6f)
        assertEquals(-1f, FaceMath.cosine(v(1f, 1f), v(-1f, -1f)), 1e-6f)
        assertEquals(0f, FaceMath.cosine(v(1f, 2f), v(1f, 2f, 3f)), 0f)
        assertEquals(0f, FaceMath.cosine(v(0f, 0f), v(1f, 2f)), 0f)
        assertEquals(0f, FaceMath.cosine(floatArrayOf(), floatArrayOf()), 0f)
    }

    @Test
    fun normalizeGivesLengthOne() {
        val n = FaceMath.normalize(v(3f, 4f))
        assertArrayEquals(v(0.6f, 0.8f), n, 1e-6f)
        assertArrayEquals(v(0f, 0f), FaceMath.normalize(v(0f, 0f)), 0f)
    }

    @Test
    fun theBestSavedFaceDecides() {
        val saved = listOf(v(1f, 0f), v(0f, 1f))
        assertEquals(1f, FaceMath.bestSimilarity(saved, v(0f, 5f)), 1e-6f)
        assertEquals(-1f, FaceMath.bestSimilarity(emptyList(), v(1f, 0f)), 0f)
    }

    @Test
    fun meanPointsBetweenTheFaces() {
        val m = FaceMath.mean(listOf(v(1f, 0f), v(0f, 1f)))
        assertEquals(0.7071f, m[0], 1e-3f)
        assertEquals(0.7071f, m[1], 1e-3f)
        assertEquals(0, FaceMath.mean(emptyList()).size)
    }

    @Test
    fun standardizeGivesZeroMeanAndUnitSpread() {
        val s = FaceMath.standardize(floatArrayOf(0f, 100f, 200f, 300f))
        assertEquals(0.0, s.map { it.toDouble() }.average(), 1e-5)
        assertEquals(1.0, Math.sqrt(s.map { it.toDouble() * it }.average()), 1e-5)
        // a flat picture does not blow up
        assertTrue(FaceMath.standardize(floatArrayOf(7f, 7f, 7f, 7f)).all { it == 0f })
    }

    @Test
    fun softmaxSumsToOne() {
        val s = FaceMath.softmax(v(1f, 2f, 3f))
        assertEquals(1f, s.sum(), 1e-6f)
        assertTrue(s[2] > s[1] && s[1] > s[0])
        assertEquals(1f, FaceMath.softmax(v(1000f, 0f, 0f)).sum(), 1e-6f)
    }

    @Test
    fun aLiveFaceIsTheMiddleAnswer() {
        val real = FaceMath.liveResult(v(0f, 5f, 0f), v(0f, 4f, 1f))
        assertTrue(real.first && real.second > 0.5f)
        assertFalse(FaceMath.liveResult(v(5f, 0f, 0f), v(4f, 1f, 0f)).first)
        assertFalse(FaceMath.liveResult(v(1f, 2f), v(1f, 2f, 3f)).first)
    }

    @Test
    fun theScaledBoxStaysInsideThePicture() {
        // a 100x100 face in the middle of a 640x480 picture, widened by 2.7 but held to the picture height
        val box = FaceMath.scaledBox(640, 480, 270, 190, 100, 100, 2.7f)
        assertEquals(4, box.size)
        assertTrue(box[0] >= 0 && box[1] >= 0 && box[2] <= 639 && box[3] <= 479)
        assertEquals(box[2] - box[0], box[3] - box[1])
        assertEquals(185, box[0])
        // a face at the corner is pushed back inside
        val corner = FaceMath.scaledBox(640, 480, 0, 0, 100, 100, 4f)
        assertEquals(0, corner[0]); assertEquals(0, corner[1])
        assertTrue(corner[2] <= 639 && corner[3] <= 479)
        // an empty box gives the whole picture instead of a crash
        assertEquals(639, FaceMath.scaledBox(640, 480, 0, 0, 0, 0, 2.7f)[2])
    }

    @Test
    fun savedFacesSurviveBeingWrittenAndRead() {
        val list = listOf(v(0.1f, 0.2f, 0.3f), v(-1f, 0f, 1f))
        val back = FaceMath.decode(FaceMath.encode(list))
        assertEquals(2, back.size)
        assertArrayEquals(list[1], back[1], 0f)
        assertTrue(FaceMath.decode(ByteArray(3)).isEmpty())
        assertTrue(FaceMath.decode(FaceMath.encode(list).copyOf(20)).isEmpty())
        assertTrue(FaceMath.decode(FaceMath.encode(emptyList())).isEmpty())
    }
}

class FaceVerifierTest {
    private fun frame(faces: Int = 1, big: Boolean = true, live: Boolean = true, sim: Float = 0.9f) = FrameCheck(faces, big, live, sim)

    @Test
    fun threeGoodPicturesInARowOpenIt() {
        val v = FaceVerifier(0.7f)
        assertTrue(v.onFrame(frame(), 0) is ScanResult.Waiting)
        assertTrue(v.onFrame(frame(), 200) is ScanResult.Waiting)
        assertEquals(ScanResult.Passed, v.onFrame(frame(), 400))
    }

    @Test
    fun aBadPictureStartsTheCountAgain() {
        val v = FaceVerifier(0.7f)
        v.onFrame(frame(), 0); v.onFrame(frame(), 100)
        val miss = v.onFrame(frame(sim = 0.3f), 200)
        assertEquals(ScanResult.Waiting("Not recognised yet"), miss)
        assertTrue(v.onFrame(frame(), 300) is ScanResult.Waiting)
        assertTrue(v.onFrame(frame(), 400) is ScanResult.Waiting)
        assertEquals(ScanResult.Passed, v.onFrame(frame(), 500))
    }

    @Test
    fun theHintsSayWhatIsWrong() {
        val v = FaceVerifier(0.7f)
        assertEquals(ScanResult.Waiting("Look at the camera"), v.onFrame(frame(faces = 0), 0))
        assertEquals(ScanResult.Waiting("Only you should be in view"), v.onFrame(frame(faces = 2), 10))
        assertEquals(ScanResult.Waiting("Come a little closer"), v.onFrame(frame(big = false), 20))
        assertEquals(ScanResult.Waiting("That does not look like a live face"), v.onFrame(frame(live = false), 30))
    }

    @Test
    fun aPhotoOrAnotherPersonNeverPassesAndTheScanTimesOut() {
        val v = FaceVerifier(0.7f)
        var t = 0L
        var last: ScanResult = ScanResult.Waiting("")
        while (t <= 9_000L) { last = v.onFrame(frame(live = false), t); if (last is ScanResult.Failed) break; t += 250 }
        assertEquals(ScanResult.Failed("That does not look like a live face"), last)
        val other = FaceVerifier(0.7f)
        other.onFrame(frame(sim = 0.2f), 0)
        assertEquals(ScanResult.Failed("Not recognised yet"), other.onFrame(frame(sim = 0.2f), 8_000))
    }

    @Test
    fun aCameraThatSendsNothingStillRunsOut() {
        val v = FaceVerifier(0.7f)
        assertEquals(null, v.expire(100_000))
        v.onFrame(frame(faces = 0), 1_000)
        assertEquals(null, v.expire(5_000))
        assertEquals(ScanResult.Failed("Look at the camera"), v.expire(9_000))
    }

    @Test
    fun theThresholdIsInclusive() {
        val v = FaceVerifier(0.7f, needed = 1)
        assertEquals(ScanResult.Passed, v.onFrame(frame(sim = 0.7f), 0))
    }
}

class EnrolCollectorTest {
    private fun face(a: Float, b: Float) = floatArrayOf(a, b, 0f)

    @Test
    fun picturesAreSpacedOut() {
        val c = EnrolCollector(needed = 3, minGapMs = 450)
        assertEquals(EnrolCollector.Outcome.ACCEPTED, c.add(face(1f, 0f), 0))
        assertEquals(EnrolCollector.Outcome.TOO_SOON, c.add(face(1f, 0.1f), 200))
        assertEquals(EnrolCollector.Outcome.ACCEPTED, c.add(face(1f, 0.1f), 500))
        assertEquals(2, c.count)
        assertFalse(c.done)
    }

    @Test
    fun aDifferentFaceIsRefused() {
        val c = EnrolCollector(needed = 3, minGapMs = 0)
        c.add(face(1f, 0f), 0)
        assertEquals(EnrolCollector.Outcome.DIFFERENT_FACE, c.add(face(0f, 1f), 10))
        assertEquals(1, c.count)
    }

    @Test
    fun itIsDoneAfterTheWantedNumberAndKeepsLengthOneFaces() {
        val c = EnrolCollector(needed = 2, minGapMs = 0)
        c.add(face(2f, 0f), 0); c.add(face(3f, 0.2f), 1)
        assertTrue(c.done)
        assertEquals(EnrolCollector.Outcome.TOO_SOON, c.add(face(1f, 0f), 2))
        assertEquals(2, c.embeddings().size)
        assertEquals(1f, FaceMath.cosine(c.embeddings()[0], face(1f, 0f)), 1e-6f)
        assertEquals(1.0, c.embeddings()[0].sumOf { it.toDouble() * it }, 1e-6)
    }
}
