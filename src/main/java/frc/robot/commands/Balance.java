package frc.robot.commands;

import edu.wpi.first.wpilibj2.command.CommandBase;
import frc.robot.subsystems.Drivetrain;

// does not work correctly

public class Balance extends CommandBase {
    private Drivetrain drivetrain;
    public double maxSpeed = 1;

    public Balance(Drivetrain drivetrain) {
        this.addRequirements(drivetrain);
        this.drivetrain = drivetrain;
    }

    @Override
    public void initialize() {
        this.drivetrain.halt();

        this.drivetrain.setPIDSlot(0);
        this.drivetrain.setPIDPigeonSensors();
        this.drivetrain.setPIDSetpoint(0);
        this.drivetrain.m_diffDrive.setSafetyEnabled(false);
    }

    @Override
    public void execute() {
        System.out.println("a");
    }

    @Override
    public void end(boolean interrupted) {
        this.drivetrain.setPIDSlot(1);
        this.drivetrain.setIntegratedSensors();
        this.drivetrain.m_diffDrive.setSafetyEnabled(false);
        
        this.drivetrain.halt();
    }
}
