package com.example.demo.application.services;

import com.example.demo.domain.comment.CommentRepository;
import com.example.demo.domain.redemption.RedemptionRepository;
import com.example.demo.domain.review.ReviewRepository;
import com.example.demo.domain.sweepstake.SweepstakeEntryRepository;
import com.example.demo.domain.sweepstake.WinnerRepository;
import com.example.demo.domain.user.User;
import com.example.demo.domain.user.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserDeletionService {

    private final WinnerRepository winnerRepository;
    private final SweepstakeEntryRepository sweepstakeEntryRepository;
    private final RedemptionRepository redemptionRepository;
    private final CommentRepository commentRepository;
    private final ReviewRepository reviewRepository;
    private final UserRepository userRepository;

    @PersistenceContext
    private EntityManager entityManager;

    public UserDeletionService(
            WinnerRepository winnerRepository,
            SweepstakeEntryRepository sweepstakeEntryRepository,
            RedemptionRepository redemptionRepository,
            CommentRepository commentRepository,
            ReviewRepository reviewRepository,
            UserRepository userRepository) {
        this.winnerRepository = winnerRepository;
        this.sweepstakeEntryRepository = sweepstakeEntryRepository;
        this.redemptionRepository = redemptionRepository;
        this.commentRepository = commentRepository;
        this.reviewRepository = reviewRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public void deleteAllUserData(User user) {
        Long userId = user.getId();

        // Usamos SQL nativo para todo para garantizar el orden de ejecución
        // sin que Hibernate reordene las operaciones en su ActionQueue

        // 1. Support messages (hijo de support_tickets)
        entityManager.createNativeQuery(
                        "DELETE sm FROM support_messages sm " +
                                "JOIN support_tickets st ON sm.ticket_id = st.id " +
                                "WHERE st.user_id = :uid")
                .setParameter("uid", userId).executeUpdate();

        // 2. Support tickets
        entityManager.createNativeQuery("DELETE FROM support_tickets WHERE user_id = :uid")
                .setParameter("uid", userId).executeUpdate();

        // 3. Winners
        entityManager.createNativeQuery("DELETE FROM winners WHERE user_id = :uid")
                .setParameter("uid", userId).executeUpdate();

        // 4. SweepstakeEntries
        entityManager.createNativeQuery("DELETE FROM sweepstake_entries WHERE user_id = :uid")
                .setParameter("uid", userId).executeUpdate();

        // 5. Redemptions
        entityManager.createNativeQuery("DELETE FROM redemptions WHERE user_id = :uid")
                .setParameter("uid", userId).executeUpdate();

        // 6. Comments
        entityManager.createNativeQuery("DELETE FROM comments WHERE user_id = :uid")
                .setParameter("uid", userId).executeUpdate();

        // 7. Reviews
        entityManager.createNativeQuery("DELETE FROM reviews WHERE user_id = :uid")
                .setParameter("uid", userId).executeUpdate();

        // 8. PointTransactions
        entityManager.createNativeQuery("DELETE FROM point_transactions WHERE user_id = :uid")
                .setParameter("uid", userId).executeUpdate();

        // 9. PremiumRedemptions
        entityManager.createNativeQuery("DELETE FROM premium_redemptions WHERE user_id = :uid")
                .setParameter("uid", userId).executeUpdate();

        // 10. PremiumDrawEntries (por las dudas)
        entityManager.createNativeQuery("DELETE FROM premium_draw_entries WHERE user_id = :uid")
                .setParameter("uid", userId).executeUpdate();

        // 11. Desvincular al usuario como ganador de premios premium
        entityManager.createNativeQuery(
                        "UPDATE premium_rewards SET winner_user_id = NULL WHERE winner_user_id = :uid")
                .setParameter("uid", userId).executeUpdate();

        // 12. UserSubscriptions
        entityManager.createNativeQuery("DELETE FROM user_subscriptions WHERE user_id = :uid")
                .setParameter("uid", userId).executeUpdate();

        // 13. Usuario — último
        entityManager.createNativeQuery("DELETE FROM users WHERE id = :uid")
                .setParameter("uid", userId).executeUpdate();
    }
}