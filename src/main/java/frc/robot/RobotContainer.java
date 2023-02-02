package frc.robot;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import edu.wpi.first.math.controller.RamseteController;
import edu.wpi.first.math.geometry.Pose2d;
import edu.wpi.first.math.geometry.Rotation2d;
import edu.wpi.first.math.geometry.Transform2d;
import edu.wpi.first.math.geometry.Translation2d;
import edu.wpi.first.math.trajectory.Trajectory;
import edu.wpi.first.math.trajectory.TrajectoryGenerator;
import edu.wpi.first.math.trajectory.TrajectoryUtil;
import edu.wpi.first.math.trajectory.Trajectory.State;
import edu.wpi.first.wpilibj.DriverStation;
import edu.wpi.first.wpilibj.Filesystem;
import edu.wpi.first.wpilibj.XboxController;
import edu.wpi.first.wpilibj.DriverStation.Alliance;
import frc.robot.Constants.AutoConstants;
import frc.robot.Constants.DrivetrainConstants;
import frc.robot.commands.POVSelector;
import frc.robot.commands.DrivetrainCommands.ApproachTag;
import frc.robot.commands.DrivetrainCommands.BalanceAUX;
import frc.robot.commands.DrivetrainCommands.BalancePID;
import frc.robot.commands.DrivetrainCommands.BalanceRollPID;
import frc.robot.commands.DrivetrainCommands.OrchestraPlayer;
import frc.robot.commands.DrivetrainCommands.RunRamseteTrajectory;
import frc.robot.commands.POVSelector.Tree;
import frc.robot.commands.DrivetrainCommands.TurnDegrees;
import frc.robot.oi.DriverOI;
import frc.robot.subsystems.Drivetrain;
import frc.robot.subsystems.Log;
import edu.wpi.first.wpilibj.smartdashboard.SendableChooser;
import edu.wpi.first.wpilibj.smartdashboard.SmartDashboard;
import edu.wpi.first.wpilibj2.command.Command;
import frc.robot.subsystems.Transmission;
import edu.wpi.first.wpilibj2.command.RunCommand;
import edu.wpi.first.wpilibj2.command.SequentialCommandGroup;
import edu.wpi.first.wpilibj2.command.WaitCommand;
import edu.wpi.first.wpilibj2.command.InstantCommand;
import edu.wpi.first.wpilibj2.command.RamseteCommand;

/**
 * This class is where the bulk of the robot should be declared. Since
 * Command-based is a
 * "declarative" paradigm, very little robot logic should actually be handled in
 * the {@link Robot}
 * periodic methods (other than the scheduler calls). Instead, the structure of
 * the robot (including
 * subsystems, commands, and button mappings) should be declared here.
 */
public class RobotContainer {
	// Direction around the Charger Station
	public static enum Direction {
		Left,
		Right,
		Unspecified
	}

	// The Robot's Subsystems
	public final Transmission transmission = new Transmission();
	public final Drivetrain drivetrain = new Drivetrain(this.transmission::getGearState);

	// XBox Controllers
	private final XboxController driverController = new XboxController(0);
	private final DriverOI driverOI = new DriverOI(this.driverController);

	// Create SmartDashboard chooser for autonomous routines
	private final SendableChooser<Command> chooser = new SendableChooser<>();

	public static DriverStation.Alliance alliance = DriverStation.Alliance.Blue;

	private Direction direction = Direction.Unspecified;

	/**
	 * The container for the robot. Contains subsystems, OI devices, and commands.
	 */
	public RobotContainer() {
		this.configureAutoChooser();

		// Configure default commands, button bindings, and shuffleboard
		this.configureSubsystems();
		SmartDashboard.putNumber("Tag6 X", FieldConstants.aprilTags.get(6).toPose2d().getX());
		SmartDashboard.putNumber("Tag6 Y", FieldConstants.aprilTags.get(6).toPose2d().getY());
	}

	/**
	 * Configure all subsystems with their default command, button commands,
	 * and Shuffleboard output
	 */
	private void configureSubsystems() {
		this.configureDrivetrain();
	}

	public void setAlliance(Alliance alliance) {
		RobotContainer.alliance = alliance;
	}

	public void configureDrivetrain() {
		// Configure default commands
		// Set the default drive command to split-stick arcade drive
		// A split-stick arcade command, with forward/backward controlled by the left
		// stick, and turning controlled by the right.
		this.drivetrain.setDefaultCommand(
			new RunCommand(
				() -> this.drivetrain.diffDrive.arcadeDrive(
					this.driverOI.getMoveSupplier().getAsDouble() * DrivetrainConstants.arcadeDriveMultiplier,
					this.driverOI.getRotateSupplier().getAsDouble() * DrivetrainConstants.arcadeDriveMultiplier
				),
				this.drivetrain
			)
		);

		// Configure button commands
		this.driverOI.getShiftLowButton().onTrue(new InstantCommand(this.transmission::setLow, this.transmission));
		this.driverOI.getShiftHighButton().onTrue(new InstantCommand(this.transmission::setHigh, this.transmission));
		this.driverOI.getOrchestraButton().whileTrue(
			new OrchestraPlayer(
				this.drivetrain,
				Filesystem.getDeployDirectory().toPath().resolve("homedepot.chrp").toString()
			)
		);
		this.driverOI.getBalanceButton().whileTrue(BalancePID.manual(this.drivetrain));
		//this.driverOI.getRollButton().whileTrue(BalanceRollPID.manual(this.drivetrain));
		this.driverOI.getApproachTagButton().whileTrue(ApproachTag.manual(this.drivetrain));
		this.driverOI.getBalanceAuxButton().whileTrue(BalanceAUX.manual(this.drivetrain));
		this.driverOI.getResetGyroButton().onTrue(new InstantCommand(() -> {
			this.drivetrain.zeroGyro();
			this.drivetrain.resetEncoders();
		}, this.drivetrain));

		//POV tree for dynamic trajectories? (use TBD)
		// this.driverOI.getTestButton().toggleOnTrue(new POVSelector(
		// 	this.driverOI,
		// 	path -> Log.writeln("POV Selector step: ", String.join(",", path)),
		// 	(str, path) -> Log.writeln("POV Selector finish: ", str, " (", String.join(",", path), ')'),
		// 	new Tree("Deposit",
		// 		new Tree("Center Station",
		// 			new Tree("Center Position",
		// 				new Tree("Top", new int[] { 2, 2, 3 }),
		// 				new Tree("Middle", new int[] { 2, 2, 2 }),
		// 				new Tree("Bottom", new int[] { 2, 2, 1 }),
		// 				new Tree("Top", new int[] { 2, 2, 3 })
		// 			),
		// 			new Tree("Right Position",
		// 				new Tree("Top", new int[] { 2, 3, 3 }),
		// 				new Tree("Middle", new int[] { 2, 3, 2 }),
		// 				new Tree("Bottom", new int[] { 2, 3, 1 }),
		// 				new Tree("Top", new int[] { 2, 3, 3 })
		// 			),
		// 			new Tree(),
		// 			new Tree("Left Position",
		// 				new Tree("Top", new int[] { 2, 1, 3 }),
		// 				new Tree("Middle", new int[] { 2, 1, 2 }),
		// 				new Tree("Bottom", new int[] { 2, 1, 1 }),
		// 				new Tree("Top", new int[] { 2, 1, 3 })
		// 			)
		// 		),
		// 		new Tree("Right Station",
		// 			new Tree("Center Position",
		// 				new Tree("Top", new int[] { 3, 2, 3 }),
		// 				new Tree("Middle", new int[] { 3, 2, 2 }),
		// 				new Tree("Bottom", new int[] { 3, 2, 1 }),
		// 				new Tree("Top", new int[] { 3, 2, 3 })
		// 			),
		// 			new Tree("Right Position",
		// 				new Tree("Top", new int[] { 3, 3, 3 }),
		// 				new Tree("Middle", new int[] { 3, 3, 2 }),
		// 				new Tree("Bottom", new int[] { 3, 3, 1 }),
		// 				new Tree("Top", new int[] { 3, 3, 3 })
		// 			),
		// 			new Tree(),
		// 			new Tree("Left Position",
		// 				new Tree("Top", new int[] { 3, 1, 3 }),
		// 				new Tree("Middle", new int[] { 3, 1, 2 }),
		// 				new Tree("Bottom", new int[] { 3, 1, 1 }),
		// 				new Tree("Top", new int[] { 3, 1, 3 })
		// 			)
		// 		),
		// 		new Tree(),
		// 		new Tree("Left Station",
		// 			new Tree("Center Position",
		// 				new Tree("Top", new int[] { 1, 2, 3 }),
		// 				new Tree("Middle", new int[] { 1, 2, 2 }),
		// 				new Tree("Bottom", new int[] { 1, 2, 1 }),
		// 				new Tree("Top", new int[] { 1, 2, 3 })
		// 			),
		// 			new Tree("Right Position",
		// 				new Tree("Top", new int[] { 1, 3, 3 }),
		// 				new Tree("Middle", new int[] { 1, 3, 2 }),
		// 				new Tree("Bottom", new int[] { 1, 3, 1 }),
		// 				new Tree("Top", new int[] { 1, 3, 3 })
		// 			),
		// 			new Tree(),
		// 			new Tree("Left Position",
		// 				new Tree("Top", new int[] { 1, 1, 3 }),
		// 				new Tree("Middle", new int[] { 1, 1, 2 }),
		// 				new Tree("Bottom", new int[] { 1, 1, 1 }),
		// 				new Tree("Top", new int[] { 1, 1, 3 })
		// 			)
		// 		)
		// 	)
		// ));
		
		this.driverOI.getStartButton().onTrue(this.generateRamseteCommand(() -> this.generateTrajectory(FieldConstants.tag6)));
		// this.driverOI.getGoToTag7Button().onTrue(this.generateRamseteCommand(() -> this.generateTrajectory(FieldConstants.tag7)));
		// this.driverOI.getGoToTag8Button().onTrue(this.generateRamseteCommand(() -> this.generateTrajectory(FieldConstants.tag8)));
	}


	private void configureAutoChooser() {
		// this.chooser.setDefaultOption("Test Dropoff", this.generateRamseteCommand(() -> this.generateTrajectory(FieldConstants.tag6)));

		// chooser.addOption(
		// 	"test",
		// 	new SequentialCommandGroup(
		// 		new WaitCommand(.1),
		// 		new RunRamseteTrajectory(drivetrain, loadTrajectory("BackUpToBalance"))
		// 	)
		// );
		// chooser.addOption(
		// 	"testing",
		// 	new SequentialCommandGroup(
		// 		new WaitCommand(.1),
		// 		new DriveTime(.5, 2, drivetrain)
		// 	)
		// );

		// chooser.addOption(
		// 	"auto 3",
		// 	new SequentialCommandGroup(
		// 		new WaitCommand(.1),
		// 		new RunRamseteTrajectory(drivetrain, loadTrajectory("Auto3")),
		// 		new BalanceAUX(drivetrain, false, 15)
		// 	)
		// );

		// chooser.addOption(
		// 	"auto 2",
		// 	new SequentialCommandGroup(
		// 		new WaitCommand(.1),
		// 		new RunRamseteTrajectory(drivetrain, loadTrajectory("Auto2")),
		// 		new BalanceAUX(drivetrain, false, 15)
		// 	)
		// );

		// chooser.setDefaultOption(
		// 	"Rotate 8", 
		// 	new SequentialCommandGroup( 
		// 		new RunRamseteTrajectory(drivetrain, loadTrajectory("Rotate8")),
		// 		// new RunRamseteTrajectory(drivetrain, loadTrajectory("Rotate8Forward")),
		// 		// new RunRamseteTrajectory(drivetrain, loadTrajectory("Rotate8Back")))
		// 		new RunRamseteTrajectory(drivetrain, loadTrajectory("AroundChargeStation")))
		// 	);

		chooser.setDefaultOption(
			"Tag6 Routines1", 
			new SequentialCommandGroup( 
				new RunRamseteTrajectory(drivetrain, loadTrajectory("Tag6-Rotate3")),
				//new TurnDegrees(.5, 0, drivetrain),
				//new RunRamseteTrajectory(drivetrain, loadTrajectory("Rotate3")),
				// new RunRamseteTrajectory(drivetrain, loadTrajectory("Rotate8Back")))
				new RunRamseteTrajectory(drivetrain, loadTrajectory("Rotate3-Cargo5")),
				new RunRamseteTrajectory(drivetrain, loadTrajectory("Cargo5-Tag6")))
		);

		chooser.addOption(
			"Tag7 Routines1", 
			new SequentialCommandGroup( 
				new RunRamseteTrajectory(drivetrain, loadTrajectory("Tag7-Rotate3")),
				//new TurnDegrees(.5, 0, drivetrain),
				//new RunRamseteTrajectory(drivetrain, loadTrajectory("Rotate3")),
				// new RunRamseteTrajectory(drivetrain, loadTrajectory("Rotate8Back")))
				new RunRamseteTrajectory(drivetrain, loadTrajectory("Rotate3-Cargo5")),
				new RunRamseteTrajectory(drivetrain, loadTrajectory("Cargo5-Tag6")))
		);

		chooser.addOption("Calibrate Trajectory", 
			new RunRamseteTrajectory(drivetrain, calibrateTrajectory()));

		chooser.addOption(
			"Back up and balance",
			new SequentialCommandGroup(
				new WaitCommand(.1),
				new RunRamseteTrajectory(drivetrain, loadTrajectory("BackUpToBalance")),
				new BalanceAUX(drivetrain, false, 15)
			)
		);

		chooser.addOption(
			"Curve right around Charging Station and balance",
			new SequentialCommandGroup(
				new WaitCommand(.1),
				new RunRamseteTrajectory(drivetrain, loadTrajectory("Auto1")),
				// Todo: find right time/speed to get onto teeter totter
				// new DriveTime(-.4, .5, this.drivetrain),
				new BalanceAUX(drivetrain, false, 15)
			)
		);

		SmartDashboard.putData("AutoRoutineChooser", chooser);
	}

	/**
	 * Generate a trajectory following Ramsete command
	 * 
	 * This is very similar to the WPILib RamseteCommand example. It uses
	 * constants defined in the Constants.java file. These constants were 
	 * found empirically by using the frc-characterization tool.
	 * 
	 * @return A SequentialCommand that sets up and executes a trajectory following Ramsete command
	 */
  	private Command generateRamseteCommand(Supplier<Trajectory> trajectory) {
		RamseteCommand ramseteCommand = new RamseteCommand(
			trajectory.get(),
			drivetrain::getPose,
			new RamseteController(AutoConstants.kRamseteB, AutoConstants.kRamseteZeta),
			DrivetrainConstants.kDriveKinematics,
			drivetrain::setOutputMetersPerSecond,
			drivetrain);

		drivetrain.resetOdometry(trajectory.get().getInitialPose());

		// Set up a sequence of commands
		// First, we want to reset the drivetrain odometry
		return new InstantCommand(() -> drivetrain.resetOdometry(trajectory.get().getInitialPose()), drivetrain)
			// next, we run the actual ramsete command
			.andThen(ramseteCommand)
			// make sure that the robot stops
			.andThen(new InstantCommand(() -> drivetrain.tankDriveVolts(0, 0), drivetrain))
			// set the direction back to unspecified
			.andThen(new InstantCommand(() -> direction = Direction.Unspecified));
	} 

	public Trajectory loadTrajectory(String trajectoryJSON) {
		Path trajectoryPath = Filesystem
			.getDeployDirectory()
			.toPath()
			.resolve("paths/output/" + trajectoryJSON + ".wpilib.json");
		
		try {
			return TrajectoryUtil.fromPathweaverJson(trajectoryPath);
		} catch (IOException ex) {
			Log.error(ex);
			return null;
		}
	}

	/**
	 * Drives a straight line 2 meters so as you can calibrate your Romi
	 * You should make sure that the robot ends up right on the 2 meter mark.
	 *
	 */
  	public Trajectory calibrateTrajectory() {
		// Note that all coordinates are in meters, and follow NWU conventions.
		return TrajectoryGenerator.generateTrajectory(
			// Start at the origin facing the +X direction
			new Pose2d(0, 0, new Rotation2d(0)),
			// List.of(new Translation2d(2.0, 0.0)) 
			List.of(),
			new Pose2d(4.0, 0.0, new Rotation2d(0)), // left
			DrivetrainConstants.kTrajectoryConfig);
  	}

	/**
	 * 
	 * @param endPose pose where robot should end
	 * @param direction 0 for left, 1 for right, 2 for robot to decide
	 * @return
	 */
	public Trajectory generateTrajectory(Pose2d endPose) {
        Pose2d startPose = this.drivetrain.getLimelightPose();

        Log.writeln("Generate start Pose: " + startPose);
        SmartDashboard.putNumber("Start Pose X", startPose.getX());
        SmartDashboard.putNumber("Start Pose Y", startPose.getY());
        SmartDashboard.putNumber("Start Pose Heading", startPose.getRotation().getDegrees());
    
        SmartDashboard.putNumber("End Pose X", endPose.getX());
        SmartDashboard.putNumber("End Pose Y", endPose.getY());
        SmartDashboard.putNumber("End Pose Heading", endPose.getRotation().getDegrees());
        
		Trajectory trajectory;
		List<Translation2d> waypoints = new ArrayList<>();

		Log.writeln("Alliance:" + alliance);
        if(alliance == DriverStation.Alliance.Red){
        	// for red, left and right
        	//if direction is specified left, or direction is unspecified and Y is on left side of field...
        	if(direction == Direction.Left || ((direction == Direction.Unspecified ) && (drivetrain.isLeftOfChargingStation()))){
				Log.writeln("Red left");
        		trajectory = TrajectoryGenerator.generateTrajectory(startPose, 
					List.of(FieldConstants.Waypoints.leftRed1, FieldConstants.Waypoints.leftRed2),
					endPose, DrivetrainConstants.kTrajectoryConfig);
        	} else {
				Log.writeln("Red right");
        		trajectory = TrajectoryGenerator.generateTrajectory(startPose, 
					List.of(FieldConstants.Waypoints.rightRed1, FieldConstants.Waypoints.rightRed2), 
					endPose, DrivetrainConstants.kTrajectoryConfig);
        	}
        } else {
        	// for blue, left and right
        	if(direction == Direction.Left || ((direction == Direction.Unspecified) && (drivetrain.isLeftOfChargingStation()))){
        		Log.writeln("Blue left");
				Log.writeln("CS Center" + FieldConstants.Community.chargingStationCenterY);
				if (startPose.getX() > FieldConstants.Waypoints.leftBlue1.getX()) {
					waypoints.add(FieldConstants.Waypoints.leftBlue1);
				}
				if (startPose.getX() > FieldConstants.Waypoints.leftBlue2.getX()) {
					waypoints.add(FieldConstants.Waypoints.leftBlue2);
				}				

				trajectory = TrajectoryGenerator.generateTrajectory(startPose, 
					waypoints,
        			endPose, DrivetrainConstants.kTrajectoryConfig);
        	} else {
				Log.writeln("Blue right");
				if (startPose.getX() > FieldConstants.Waypoints.rightBlue1.getX()) {
					waypoints.add(FieldConstants.Waypoints.rightBlue1);
				}		
				if (startPose.getX() > FieldConstants.Waypoints.rightBlue2.getX()) {
					waypoints.add(FieldConstants.Waypoints.rightBlue2);
				} 		
				
        		trajectory = TrajectoryGenerator.generateTrajectory(startPose, 
					waypoints,
        			endPose, DrivetrainConstants.kTrajectoryConfig);
        	}
        }
    	
		Log.writeln("Initial Pose: " + trajectory.getInitialPose());
		Log.writeln("Waypoints:" + waypoints);
		Log.writeln("End Pose:" + endPose);

        //printTrajectory(trajectory);

        return trajectory;
    }

	public void printTrajectory(Trajectory trajectory) {
		List<State> states = trajectory.getStates();

		for (int i = 1; i < states.size(); i++) {
			var state = states.get(i);
			Log.writeln("Time:" + state.timeSeconds + 
						" X:" + state.poseMeters.getX() +
						" Y:" + state.poseMeters.getY() +
						" Vel:" + state.velocityMetersPerSecond);
		}  

		Log.writeln("Traj: " + trajectory.getTotalTimeSeconds()); 
	}	

	public Command getAutonomousCommand() {
		return this.chooser.getSelected();
	}
}
