package primemover.multilogin.mlx;

/** Releases a profile's lock in Multilogin's cloud. */
public interface ProfileUnlocker {

	/**
	 * @param profileId the one profile to unlock; never blank
	 * @return true if Multilogin accepted the request
	 */
	boolean unlock(String profileId);
}
