import java.io.File;

public class Helper {

    /**
     * save the first 4 bytes of bytes1 to the first 4 bytes of bytes2
     *
     * @param bytes1
     * @param bytes2
     */
    public static void save4Bytes(byte[] bytes1, byte[] bytes2) {
        for (int i = 0; i < 4; i++) {
            bytes2[i] = bytes1[i];
        }
    }

    /**
     * get the first 4 bytes from bytes2
     *
     * @param bytes2
     * @return
     */
    public static byte[] get4Bytes(byte[] bytes2) {
        byte[] get = new byte[4];
        System.arraycopy(bytes2, 0, get, 0, 4);
        return get;
    }

    /**
     * turn an integer a to byte array of size 4.
     *
     * @param a
     * @return
     */
    public static byte[] intToByteArray(int a) {
        return new byte[]{
                (byte) ((a >> 24) & 0xFF),
                (byte) ((a >> 16) & 0xFF),
                (byte) ((a >> 8) & 0xFF),
                (byte) (a & 0xFF)
        };
    }

    /**
     * turn a byte array of size 4 to an integer
     *
     * @param b
     * @return
     */
    public static int byteArrayToInt(byte[] b) {
        return b[3] & 0xFF |
                (b[2] & 0xFF) << 8 |
                (b[1] & 0xFF) << 16 |
                (b[0] & 0xFF) << 24;
    }

    public static void sleep(int timeout) {
        try {
            Thread.sleep(timeout);
        } catch (InterruptedException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }
}
