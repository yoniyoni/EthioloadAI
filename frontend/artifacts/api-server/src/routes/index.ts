import { Router, type IRouter } from "express";
import healthRouter from "./health";
import authRouter from "./auth";
import usersRouter from "./users";
import driversRouter from "./drivers";
import vehiclesRouter from "./vehicles";
import freightRouter from "./freight";
import applicationsRouter from "./applications";
import matchingRouter from "./matching";
import ratingsRouter from "./ratings";
import trackingRouter from "./tracking";
import adminRouter from "./admin";
import aiRouter from "./ai";
import paymentsRouter from "./payments";
import disputesRouter from "./disputes";
import messagesRouter from "./messages";
import contractsRouter from "./contracts";

const router: IRouter = Router();

router.use(healthRouter);
router.use(authRouter);
router.use(usersRouter);
router.use(driversRouter);
router.use(vehiclesRouter);
router.use(freightRouter);
router.use(applicationsRouter);
router.use(matchingRouter);
router.use(ratingsRouter);
router.use(trackingRouter);
router.use(paymentsRouter);
router.use(disputesRouter);
router.use(messagesRouter);
router.use(contractsRouter);
router.use(adminRouter);
router.use(aiRouter);

export default router;
